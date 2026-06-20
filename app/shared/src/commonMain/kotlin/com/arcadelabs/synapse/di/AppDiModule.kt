package com.arcadelabs.synapse.di

import com.arcadelabs.synapse.features.folders.ui.FolderViewModel
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import org.koin.dsl.module

val appDiModule = module {
    factory { FolderViewModel(get()) }
    factory { DeviceViewModel(get()) }
    factory { StatusViewModel(get()) }
}

