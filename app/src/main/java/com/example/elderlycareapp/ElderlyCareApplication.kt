package com.example.elderlycareapp

import android.app.Application
import android.content.Context
import com.example.elderlycareapp.utils.FirebaseConfig

class ElderlyCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase with secure configuration
        FirebaseConfig.initializeFirebase(applicationContext)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // You can add MultiDex here if needed
        // MultiDex.install(this)
    }
}
