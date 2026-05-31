package com.aistudio.sharmakhata.pqmzvk

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import com.aistudio.sharmakhata.pqmzvk.util.Constants

@HiltAndroidApp
class GrahbookAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            GrahbookApp.init(this)
        } catch (e: Exception) {
            android.util.Log.e("GrahbookApp", "init failed (non-fatal)", e)
        }
        try {
            SentryAndroid.init(this) { options ->
                options.dsn = Constants.SENTRY_DSN
                options.tracesSampleRate = 0.2
            }
        } catch (e: Exception) {
            android.util.Log.w("GrahbookApp", "Sentry init failed (non-fatal)", e)
        }
    }
}
