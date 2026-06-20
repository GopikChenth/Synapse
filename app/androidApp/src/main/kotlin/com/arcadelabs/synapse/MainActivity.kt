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
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arcadelabs.synapse.service.SyncthingService

class MainActivity : ComponentActivity() {

    private var onDirectorySelectedCallback: ((String) -> Unit)? = null
    private val dirPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val path = convertUriToPath(this, it)
            onDirectorySelectedCallback?.invoke(path)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
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
                }
            )
        }

        checkAndRequestPermissions()
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