package com.arcadelabs.synapse.core.prefs

/**
 * Platform-agnostic settings contract for Synapse.
 *
 * Each platform will provide its own backing store:
 *   - Android  → SharedPreferences / DataStore
 *   - iOS      → NSUserDefaults
 *   - Desktop  → java.util.prefs.Preferences
 *   - Web      → localStorage
 */
interface PreferencesHelper {
    /** Base URL of the Syncthing REST API, e.g. "http://localhost:8384" */
    var apiBaseUrl: String

    /** Syncthing X-API-Key header value */
    var apiKey: String

    /** Whether to accept incoming folder shares automatically */
    var autoAcceptFolders: Boolean

    /** Path to Syncthing's config.xml file on disk (platform-dependent) */
    var configFilePath: String

    /** Selected app UI theme */
    var selectedTheme: String

    /** Reactive flow of the selected theme */
    val themeFlow: kotlinx.coroutines.flow.StateFlow<String>

    /** Selected theme mode: "Dark", "Light", or "System" */
    var themeMode: String

    /** Reactive flow of the selected theme mode */
    val themeModeFlow: kotlinx.coroutines.flow.StateFlow<String>

    /** Selected run behavior: "FOLLOW", "FORCE_START", "FORCE_STOP" */
    var runBehavior: String

    /** Reactive flow of the selected run behavior */
    val runBehaviorFlow: kotlinx.coroutines.flow.StateFlow<String>

    /** Whether the app should start automatically on system boot */
    var autoStart: Boolean

    /** Reactive flow of the auto start setting */
    val autoStartFlow: kotlinx.coroutines.flow.StateFlow<Boolean>

    /** Whether the Dynamic Island overlay is enabled */
    var enableDynamicIsland: Boolean

    /** Reactive flow of the Dynamic Island setting */
    val enableDynamicIslandFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
}
