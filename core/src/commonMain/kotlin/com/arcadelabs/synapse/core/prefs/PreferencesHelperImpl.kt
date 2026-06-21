package com.arcadelabs.synapse.core.prefs

import com.russhwolf.settings.Settings

class PreferencesHelperImpl(private val settings: Settings = Settings()) : PreferencesHelper {
    override var apiBaseUrl: String
        get() = settings.getString("api_base_url", "http://127.0.0.1:8384")
        set(value) {
            settings.putString("api_base_url", value)
        }

    override var apiKey: String
        get() = settings.getString("api_key", "")
        set(value) {
            settings.putString("api_key", value)
        }

    override var autoAcceptFolders: Boolean
        get() = settings.getBoolean("auto_accept_folders", false)
        set(value) {
            settings.putBoolean("auto_accept_folders", value)
        }

    override var configFilePath: String
        get() = settings.getString("config_file_path", "")
        set(value) {
            settings.putString("config_file_path", value)
        }
}
