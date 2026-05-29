package com.aistudio.sharmakhata.pqmzvk

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.aistudio.sharmakhata.pqmzvk.data.local.AppDatabase
import com.aistudio.sharmakhata.pqmzvk.receiver.ReminderScheduler
import com.aistudio.sharmakhata.pqmzvk.util.NotificationHelper
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class GrahbookApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Enable StrictMode for development to detect potential issues
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        
        // Set up global exception handler
        setupGlobalExceptionHandler()
        
        SessionManager.load(this)
        NotificationHelper.createChannels(this)
        ReminderScheduler.scheduleDailySummary(this)
    }
    
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
    
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the exception
            android.util.Log.e("GrahbookApp", "Uncaught exception in thread ${thread.name}", throwable)
            
            // Write to file for debugging
            try {
                val stackTrace = StringWriter().also {
                    PrintWriter(it).use { writer ->
                        throwable.printStackTrace(writer)
                    }
                }.toString()
                
                val logFile = File(getExternalFilesDir(null), "crash_log_${System.currentTimeMillis()}.txt")
                logFile.writeText("Thread: ${thread.name}\n\nStack Trace:\n$stackTrace")
            } catch (e: Exception) {
                android.util.Log.e("GrahbookApp", "Failed to write crash log", e)
            }
            
            // Call the default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}