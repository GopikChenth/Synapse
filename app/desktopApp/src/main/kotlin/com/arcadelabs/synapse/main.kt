package com.arcadelabs.synapse

import androidx.compose.material3.*
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
        var isReady by remember { mutableStateOf(false) }
        val preferencesHelper: com.arcadelabs.synapse.core.prefs.PreferencesHelper = org.koin.compose.koinInject()

        LaunchedEffect(Unit) {
            daemonManager.start()
        }

        LaunchedEffect(daemonState) {
            if (daemonState is DaemonState.Ready) {
                val readyState = daemonState as DaemonState.Ready
                preferencesHelper.apiBaseUrl = readyState.apiBaseUrl
                preferencesHelper.apiKey = readyState.apiKey
                isReady = true
            }
        }

        // Track window visibility
        var isWindowVisible by remember { mutableStateOf(true) }
        var hasShownBackgroundNotification by remember { mutableStateOf(false) }

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