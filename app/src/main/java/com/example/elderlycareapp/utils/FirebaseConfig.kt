package com.example.elderlycareapp.utils

import android.content.Context
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize

object FirebaseConfig {
    private var isInitialized = false

    fun initializeFirebase(context: Context) {
        if (!isInitialized) {
            // Standard Firebase initialization using google-services.json
            Firebase.initialize(context)
            isInitialized = true
            
            // You can add additional Firebase configuration here if needed
            // For example, enabling offline persistence:
            // Firebase.firestoreSettings = firestoreSettings {
            //     isPersistenceEnabled = true
            // }
        }
    }
}
