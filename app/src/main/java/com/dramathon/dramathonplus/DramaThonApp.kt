package com.dramathon.dramathonplus

import android.app.Application
import com.dramathon.dramathonplus.client.CacheManager
import com.dramathon.dramathonplus.sign.DeviceHeaders
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class DramaThonApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        CacheManager.init(this)

        // Initialize container and device headers with stable device ID
        val stableId = DeviceIdManager.getStableId(this)
        DeviceHeaders.androidId = stableId
        DeviceHeaders.deviceId = stableId

        container
    }
}
