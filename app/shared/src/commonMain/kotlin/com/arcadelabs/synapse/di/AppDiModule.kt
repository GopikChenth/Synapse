package com.arcadelabs.synapse.di

import com.arcadelabs.synapse.features.folders.ui.FolderViewModel
import org.koin.dsl.module

val appDiModule = module {
    factory { FolderViewModel(get()) }
}
