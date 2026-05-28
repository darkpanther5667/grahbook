package com.aistudio.sharmakhata.pqmzvk

import android.app.Application
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager

class GrahbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.load(this)
    }
}
