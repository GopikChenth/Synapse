package com.arcadelabs.synapse.core.prefs

/**
 * Platform-agnostic settings contract for Synapse.
 *
 * TODO: Implement using the Russhwolf multiplatform-settings library:
 *   https://github.com/russhwolf/multiplatform-settings
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
}
