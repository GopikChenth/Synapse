package com.arcadelabs.synapse

import android.app.Application
import com.arcadelabs.synapse.core.di.coreDiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SynapseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@SynapseApp)
            modules(coreDiModule)
        }
    }
}
