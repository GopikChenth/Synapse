package com.arcadelabs.synapse.di

import com.arcadelabs.synapse.features.folders.ui.FolderViewModel
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appDiModule = module {
    viewModelOf(::FolderViewModel)
    viewModelOf(::DeviceViewModel)
    viewModelOf(::StatusViewModel)
}

