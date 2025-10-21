package com.example.sampleapp

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Sample Service for DEX transformation demonstration
 *
 * This Service will have Log.d() statements automatically injected
 * into its lifecycle methods.
 */
class DataService : Service() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Simulate some work
        Thread {
            Thread.sleep(1000)
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
