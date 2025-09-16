package com.veygax.eventhorizon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPrefs = context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE)
            val rootOnBoot = sharedPrefs.getBoolean("root_on_boot", false)
            val blockerOnBoot = sharedPrefs.getBoolean("blocker_on_boot", false)
            val customLedOnBoot = sharedPrefs.getBoolean("custom_led_on_boot", false)

            if (rootOnBoot) {
                // ... root on boot logic ...
            } else if (blockerOnBoot) {
                // ... blocker on boot logic ...
            }
            
            // Separately check for custom LED on boot
            if (customLedOnBoot) {
                val r = sharedPrefs.getInt("led_red", 255)
                val g = sharedPrefs.getInt("led_green", 255)
                val b = sharedPrefs.getInt("led_blue", 255)
                val command = """
                    echo $r > /sys/class/leds/red/brightness
                    echo $g > /sys/class/leds/green/brightness
                    echo $b > /sys/class/leds/blue/brightness
                """.trimIndent()
                
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    RootUtils.runAsRoot(command)
                }
            }
        }
    }
}