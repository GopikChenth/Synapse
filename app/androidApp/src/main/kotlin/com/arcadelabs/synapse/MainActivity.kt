package com.arcadelabs.synapse

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arcadelabs.synapse.service.SyncthingService
import com.arcadelabs.synapse.features.status.ui.RunBehavior

import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {

    private var onDirectorySelectedCallback: ((String) -> Unit)? = null
    private val dirPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val path = convertUriToPath(this, it)
            onDirectorySelectedCallback?.invoke(path)
        }
    }

    private lateinit var shortcutHandler: ShortcutHandler
    private var initialDeviceId by mutableStateOf("")
    private var initialOpenAddDevice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }

        shortcutHandler = ShortcutHandler(this)
        shortcutHandler.handleIntent(intent) { scannedId ->
            initialDeviceId = scannedId
            initialOpenAddDevice = true
        }

        setContent {
            App(
                initialDeviceId = initialDeviceId,
                initialOpenAddDevice = initialOpenAddDevice,
                clearPrefilledDevice = {
                    initialDeviceId = ""
                    initialOpenAddDevice = false
                },
                openFolder = { path ->
                    try {
                        val docId = "primary:" + path.substringAfter("/storage/emulated/0/")
                        val uri = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode(docId))
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "vnd.android.document/directory")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                val uri = Uri.parse("file://$path")
                                setDataAndType(uri, "resource/folder")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        } catch (ex: Exception) {
                            try {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                            } catch (e3: Exception) {
                                e3.printStackTrace()
                            }
                        }
                    }
                },
                selectDirectory = { onPathSelected ->
                    onDirectorySelectedCallback = onPathSelected
                    dirPickerLauncher.launch(null)
                },
                scanQrCode = { onQrScanned ->
                    try {
                        val scanner = GmsBarcodeScanning.getClient(this)
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                barcode.rawValue?.let { onQrScanned(it) }
                            }
                            .addOnFailureListener { e ->
                                e.printStackTrace()
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                openUrl = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                exitApp = {
                    finishAffinity()
                },
                onRunBehaviorChanged = { behavior ->
                    val prefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
                    prefs.edit().putString("run_behavior", behavior.name).apply()
                    
                    if (behavior == RunBehavior.FORCE_STOP) {
                        val serviceIntent = Intent(this, SyncthingService::class.java).apply {
                            action = SyncthingService.ACTION_STOP
                        }
                        startService(serviceIntent)
                    } else {
                        startSyncthingService()
                    }
                },
                showNotification = { title, message ->
                    showSystemNotification(this, title, message)
                },
                deviceModelName = run {
                    val manufacturer = android.os.Build.MANUFACTURER
                    val model = android.os.Build.MODEL
                    if (model.startsWith(manufacturer, ignoreCase = true)) {
                        model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    } else {
                        manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " " + model
                    }
                }
            )
        }

        checkAndRequestPermissions()
    }

    private var notificationIdCounter = 2000

    private fun showSystemNotification(context: Context, title: String, message: String) {
        val channelId = "synapse_alert_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Synapse Alerts",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            
        manager.notify(notificationIdCounter++, builder.build())
    }

    private fun convertUriToPath(context: Context, uri: Uri): String {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val path = split.getOrNull(1) ?: ""
            return if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().absolutePath + "/" + path
            } else {
                "/storage/" + type + "/" + path
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return uri.path ?: ""
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Post Notifications Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            checkStoragePermissionAndStartService()
        }
    }

    private fun checkStoragePermissionAndStartService() {
        // 2. All Files Access Storage Permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE)
                }
            } else {
                startSyncthingService()
            }
        } else {
            // Android 10 and below: request standard storage permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            } else {
                startSyncthingService()
            }
        }
    }

    private fun startSyncthingService() {
        val serviceIntent = Intent(this, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            checkStoragePermissionAndStartService()
        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSyncthingService()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startSyncthingService()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutHandler.handleIntent(intent) { scannedId ->
            initialDeviceId = scannedId
            initialOpenAddDevice = true
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
        private const val STORAGE_PERMISSION_REQUEST_CODE = 201
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}