package com.codex.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase
import com.codex.app.utils.ThemeManager

class CodexApplication : Application() {

    companion object {
        private const val TAG = "CodexApplication"
        lateinit var instance: CodexApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemeManager.init(this)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        installAppCheck()
    }

    private fun installAppCheck() {
        val appCheck = Firebase.appCheck
        val factory: AppCheckProviderFactory = if (BuildConfig.DEBUG) {
            debugAppCheckFactory() ?: PlayIntegrityAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(factory)
        if (BuildConfig.DEBUG) {
            appCheck.addAppCheckListener {
                Log.i(TAG, "App Check active — register debug token in Firebase Console if Firestore requests fail")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun debugAppCheckFactory(): AppCheckProviderFactory? {
        return try {
            val clazz = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
            val method = clazz.getMethod("getInstance")
            method.invoke(null) as AppCheckProviderFactory
        } catch (e: Exception) {
            Log.w(TAG, "Debug App Check provider unavailable", e)
            null
        }
    }
}