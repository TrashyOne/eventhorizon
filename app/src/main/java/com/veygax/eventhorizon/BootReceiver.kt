package com.veygax.eventhorizon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scottyab.rootbeer.RootBeer
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPrefs = context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE)
            val rootOnBoot = sharedPrefs.getBoolean("root_on_boot", false)
            
            if (rootOnBoot) {
                val rootBeer = RootBeer(context)
                if (!rootBeer.isRooted) {
                    val startIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("auto_root", true)
                    }
                    context.startActivity(startIntent)
                }
            }
        }
    }
}
