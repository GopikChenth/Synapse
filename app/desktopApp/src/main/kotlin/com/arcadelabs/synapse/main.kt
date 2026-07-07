package com.arcadelabs.synapse

import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arcadelabs.synapse.core.designsystem.SynapseTheme
import com.arcadelabs.synapse.core.di.coreDiModule
import com.arcadelabs.synapse.daemon.DaemonState
import com.arcadelabs.synapse.daemon.DaemonStartupScreen
import com.arcadelabs.synapse.daemon.DesktopDaemonManager
import com.arcadelabs.synapse.di.appDiModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import java.awt.image.BufferedImage

fun main() {
    startKoin {
        allowOverride(true)
        modules(
            coreDiModule, 
            appDiModule,
            org.koin.dsl.module {
                single<com.arcadelabs.synapse.core.network.ApiKeyProvider> {
                    com.arcadelabs.synapse.core.network.JvmApiKeyProvider(get())
                }
            }
        )
    }
    application {
        val daemonManager = remember { DesktopDaemonManager() }
        val daemonState by daemonManager.state.collectAsState()
        // Track the artificial boot progress (from 0.0f to 1.0f)
        var bootProgress by remember { mutableStateOf(0f) }
        var isReady by remember { mutableStateOf(false) }
        val preferencesHelper: com.arcadelabs.synapse.core.prefs.PreferencesHelper = org.koin.compose.koinInject()
        LaunchedEffect(Unit) {
            var initial = true
            preferencesHelper.autoStartFlow.collect { enabled ->
                // At launch, re-register only when enabled (heals a stale entry after
                // the exe moves); skip the pointless delete when disabled.
                val skip = initial && !enabled
                initial = false
                if (!skip) {
                    withContext(Dispatchers.IO) {
                        setWindowsStartup(enabled)
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            daemonManager.start()
        }

        // Handle artificial boot progress transitions
        LaunchedEffect(daemonState) {
            if (daemonState is DaemonState.Ready) {
                // Daemon is ready: quickly animate the remaining progress to 100%
                val startVal = bootProgress
                androidx.compose.animation.core.animate(
                    initialValue = startVal,
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    bootProgress = value
                }
            } else {
                // Daemon is not ready: reset and animate progress up to 90%
                bootProgress = 0f
                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = 0.9f,
                    animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
                ) { value, _ ->
                    if (daemonState !is DaemonState.Ready) {
                        bootProgress = value
                    }
                }
            }
        }

        // Handle transition to application screen once daemon is Ready AND bootProgress reaches 100%
        LaunchedEffect(daemonState, bootProgress) {
            if (daemonState is DaemonState.Ready && bootProgress >= 1f) {
                val readyState = daemonState as DaemonState.Ready
                preferencesHelper.apiBaseUrl = readyState.apiBaseUrl
                preferencesHelper.apiKey = readyState.apiKey
                isReady = true
            } else if (daemonState !is DaemonState.Ready) {
                isReady = false
            }
        }

        // Track window visibility
        var isWindowVisible by remember { mutableStateOf(true) }
        var hasShownBackgroundNotification by remember { mutableStateOf(false) }

        // Optimize memory usage when backgrounded in the system tray
        LaunchedEffect(isWindowVisible, isReady) {
            val daemonPid = if (isReady) daemonManager.getProcessId() else null
            if (!isWindowVisible) {
                System.gc()
                optimizeMemory(daemonPid, background = true)
            } else {
                optimizeMemory(daemonPid, background = false)
            }
        }

        // Create tray painter
        val trayIconPainter = remember { createTrayIconPainter() }
        val trayState = rememberTrayState()

        Tray(
            state = trayState,
            icon = trayIconPainter,
            tooltip = "Synapse",
            onAction = {
                isWindowVisible = true
            },
            menu = {
                Item(
                    text = "Open Synapse",
                    onClick = {
                        isWindowVisible = true
                    }
                )
                Item(
                    text = "Exit",
                    onClick = {
                        daemonManager.stop()
                        exitApplication()
                    }
                )
            }
        )

        if (isWindowVisible) {
            Window(
                onCloseRequest = {
                    isWindowVisible = false
                    if (!hasShownBackgroundNotification) {
                        trayState.sendNotification(
                            Notification(
                                title = "Synapse",
                                message = "Synapse is still running in the background.",
                                type = Notification.Type.Info
                            )
                        )
                        hasShownBackgroundNotification = true
                    }
                },
                title = "Synapse",
            ) {
                val composeWindow = this.window
                
                // Track current theme and theme mode configurations
                val selectedTheme by preferencesHelper.themeFlow.collectAsState()
                val themeMode by preferencesHelper.themeModeFlow.collectAsState()
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                
                val isDark = when (themeMode) {
                    "Light" -> false
                    "Dark" -> true
                    else -> systemDark
                }

                // Dynamically toggle native immersive title bar dark mode on Windows
                LaunchedEffect(composeWindow, isDark) {
                    if (System.getProperty("os.name").lowercase().contains("win")) {
                        try {
                            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(
                                com.sun.jna.Native.getWindowPointer(composeWindow)
                            )
                            val attribute = 20 // DWMWA_USE_IMMERSIVE_DARK_MODE
                            val value = com.sun.jna.ptr.IntByReference(if (isDark) 1 else 0)
                            DWMAPI.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, value, 4)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }

                SynapseTheme(selectedTheme = selectedTheme, themeMode = themeMode) {
                    if (isReady) {
                        DesktopApp(
                            openFolder = { path ->
                                try {
                                    val file = java.io.File(path)
                                    if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                                        java.awt.Desktop.getDesktop().open(file)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            selectDirectory = { onPathSelected ->
                                try {
                                    val chooser = javax.swing.JFileChooser().apply {
                                        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                        dialogTitle = "Select Directory"
                                    }
                                    val result = chooser.showOpenDialog(null)
                                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        onPathSelected(chooser.selectedFile.absolutePath)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            openUrl = { url ->
                                try {
                                    if (java.awt.Desktop.isDesktopSupported()) {
                                        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            exitApp = {
                                daemonManager.stop()
                                exitApplication()
                            }
                        )
                    } else {
                        DaemonStartupScreen(
                            state = daemonState,
                            bootProgress = bootProgress,
                            onRetry = {
                                daemonManager.start()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun createTrayIconPainter(): Painter {
    val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    
    // Transparent background
    g.color = java.awt.Color(0, 0, 0, 0)
    g.fillRect(0, 0, 32, 32)
    
    // Enable anti-aliasing
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
    )
    
    // Outer semi-transparent purple glow ring (0xC084FC)
    g.color = java.awt.Color(0xC0, 0x84, 0xFC, 140)
    g.fillOval(3, 3, 26, 26)
    
    // Inner solid purple circle
    g.color = java.awt.Color(0xC0, 0x84, 0xFC)
    g.fillOval(7, 7, 18, 18)
    
    // White core representing connection point
    g.color = java.awt.Color.WHITE
    g.fillOval(12, 12, 8, 8)
    
    g.dispose()
    return BitmapPainter(image.toComposeImageBitmap())
}

private interface DWMAPI : com.sun.jna.win32.StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: com.sun.jna.platform.win32.WinDef.HWND,
        dwAttribute: Int,
        pvAttribute: com.sun.jna.ptr.IntByReference,
        cbAttribute: Int
    ): Int

    companion object {
        val INSTANCE: DWMAPI = com.sun.jna.Native.load("dwmapi", DWMAPI::class.java) as DWMAPI
    }
}

private const val PROCESS_SET_INFORMATION = 0x0200
private const val PROCESS_SET_QUOTA = 0x0100
private const val PROCESS_VM_OPERATION = 0x0008

private const val IDLE_PRIORITY_CLASS = 0x00000040
private const val NORMAL_PRIORITY_CLASS = 0x00000020

private interface Kernel32Lite : com.sun.jna.win32.StdCallLibrary {
    companion object {
        val INSTANCE = com.sun.jna.Native.load("kernel32", Kernel32Lite::class.java) as Kernel32Lite
    }
    fun GetCurrentProcess(): com.sun.jna.Pointer
    fun OpenProcess(dwDesiredAccess: Int, bInheritHandle: Boolean, dwProcessId: Int): com.sun.jna.Pointer
    fun SetPriorityClass(hProcess: com.sun.jna.Pointer, dwPriorityClass: Int): Boolean
    fun SetProcessWorkingSetSize(hProcess: com.sun.jna.Pointer, dwMinimumWorkingSetSize: Long, dwMaximumWorkingSetSize: Long): Boolean
    fun CloseHandle(hObject: com.sun.jna.Pointer): Boolean
}

private interface PsapiLite : com.sun.jna.win32.StdCallLibrary {
    companion object {
        val INSTANCE = com.sun.jna.Native.load("psapi", PsapiLite::class.java) as PsapiLite
    }
    fun EmptyWorkingSet(hProcess: com.sun.jna.Pointer): Boolean
}

private fun optimizeMemory(pid: Int?, background: Boolean) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    try {
        if (background) {
            val myProcess = Kernel32Lite.INSTANCE.GetCurrentProcess()
            PsapiLite.INSTANCE.EmptyWorkingSet(myProcess)
            Kernel32Lite.INSTANCE.SetPriorityClass(myProcess, IDLE_PRIORITY_CLASS)
            Kernel32Lite.INSTANCE.SetProcessWorkingSetSize(myProcess, -1L, -1L)

            if (pid != null && pid > 0) {
                val access = PROCESS_SET_INFORMATION or PROCESS_SET_QUOTA or PROCESS_VM_OPERATION
                val hProcess = Kernel32Lite.INSTANCE.OpenProcess(access, false, pid)
                if (hProcess != com.sun.jna.Pointer.NULL) {
                    Kernel32Lite.INSTANCE.SetPriorityClass(hProcess, IDLE_PRIORITY_CLASS)
                    Kernel32Lite.INSTANCE.SetProcessWorkingSetSize(hProcess, -1L, -1L)
                    PsapiLite.INSTANCE.EmptyWorkingSet(hProcess)
                    Kernel32Lite.INSTANCE.CloseHandle(hProcess)
                }
            }
        } else {
            val myProcess = Kernel32Lite.INSTANCE.GetCurrentProcess()
            Kernel32Lite.INSTANCE.SetPriorityClass(myProcess, NORMAL_PRIORITY_CLASS)

            if (pid != null && pid > 0) {
                val access = PROCESS_SET_INFORMATION
                val hProcess = Kernel32Lite.INSTANCE.OpenProcess(access, false, pid)
                if (hProcess != com.sun.jna.Pointer.NULL) {
                    Kernel32Lite.INSTANCE.SetPriorityClass(hProcess, NORMAL_PRIORITY_CLASS)
                    Kernel32Lite.INSTANCE.CloseHandle(hProcess)
                }
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

private fun setWindowsStartup(enabled: Boolean) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    try {
        val runKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        val appName = "Synapse"
        val hkcu = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER

        if (!enabled) {
            if (com.sun.jna.platform.win32.Advapi32Util.registryValueExists(hkcu, runKey, appName)) {
                com.sun.jna.platform.win32.Advapi32Util.registryDeleteValue(hkcu, runKey, appName)
            }
            return
        }

        val exePath = ProcessHandle.current().info().command().orElse("")

        val cmd = if (exePath.endsWith(".exe", ignoreCase = true) &&
                      !exePath.contains("java.exe", ignoreCase = true) &&
                      !exePath.contains("javaw.exe", ignoreCase = true)) {
            "\"$exePath\""
        } else {
            // Dev run (java/javaw): register the current classpath launch command
            val javaExe = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "javaw.exe"
            "\"$javaExe\" -cp \"${System.getProperty("java.class.path")}\" com.arcadelabs.synapse.MainKt"
        }

        com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(hkcu, runKey, appName, cmd)
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}