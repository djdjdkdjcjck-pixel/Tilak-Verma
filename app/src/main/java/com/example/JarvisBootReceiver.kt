package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class JarvisBootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "JarvisBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(TAG, "Device booted. Starting Jarvis Foreground Service automatically.")
            try {
                val serviceIntent = Intent(context, ZoyaForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Jarvis service on boot", e)
            }
        }
    }
}
