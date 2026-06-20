package com.arcadelabs.synapse

import android.app.Application
import com.arcadelabs.synapse.core.di.coreDiModule
import com.arcadelabs.synapse.di.appDiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SynapseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@SynapseApp)
            modules(
                coreDiModule, 
                appDiModule,
                module {
                    single<Any> { this@SynapseApp }
                }
            )
        }
    }
}
