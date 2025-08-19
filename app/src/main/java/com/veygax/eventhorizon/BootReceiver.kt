package com.veygax.eventhorizon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if user wants to root on startup
            val sharedPrefs = context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE)
            val rootOnBoot = sharedPrefs.getBoolean("root_on_boot", false)
            
            if (rootOnBoot) {
                // Start the main activity when the device boots
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(startIntent)
            }
        }
    }
}
