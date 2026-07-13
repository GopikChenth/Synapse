package com.arcadelabs.synapse.core.prefs

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.asStateFlow

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

    private val _themeFlow = kotlinx.coroutines.flow.MutableStateFlow(settings.getString("selected_theme", "Default"))
    override val themeFlow = _themeFlow.asStateFlow()

    override var selectedTheme: String
        get() = settings.getString("selected_theme", "Default")
        set(value) {
            settings.putString("selected_theme", value)
            _themeFlow.value = value
        }

    private val _themeModeFlow = kotlinx.coroutines.flow.MutableStateFlow(settings.getString("theme_mode", "Dark"))
    override val themeModeFlow = _themeModeFlow.asStateFlow()

    override var themeMode: String
        get() = settings.getString("theme_mode", "Dark")
        set(value) {
            settings.putString("theme_mode", value)
            _themeModeFlow.value = value
        }

    private val _autoStartFlow = kotlinx.coroutines.flow.MutableStateFlow(settings.getBoolean("auto_start", false))
    override val autoStartFlow = _autoStartFlow.asStateFlow()

    override var autoStart: Boolean
        get() = settings.getBoolean("auto_start", false)
        set(value) {
            settings.putBoolean("auto_start", value)
            _autoStartFlow.value = value
        }

    private val _runBehaviorFlow = kotlinx.coroutines.flow.MutableStateFlow(settings.getString("run_behavior", "FOLLOW"))
    override val runBehaviorFlow = _runBehaviorFlow.asStateFlow()

    override var runBehavior: String
        get() = settings.getString("run_behavior", "FOLLOW")
        set(value) {
            settings.putString("run_behavior", value)
            _runBehaviorFlow.value = value
        }

    private val _enableDynamicIslandFlow = kotlinx.coroutines.flow.MutableStateFlow(settings.getBoolean("enable_dynamic_island", false))
    override val enableDynamicIslandFlow = _enableDynamicIslandFlow.asStateFlow()

    override var enableDynamicIsland: Boolean
        get() = settings.getBoolean("enable_dynamic_island", false)
        set(value) {
            settings.putBoolean("enable_dynamic_island", value)
            _enableDynamicIslandFlow.value = value
        }
}
