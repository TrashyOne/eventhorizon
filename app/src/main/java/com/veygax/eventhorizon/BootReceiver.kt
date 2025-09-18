package com.veygax.eventhorizon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPrefs = context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE)
            val rootOnBoot = sharedPrefs.getBoolean("root_on_boot", false)
            val blockerOnBoot = sharedPrefs.getBoolean("blocker_on_boot", false)
            val customLedOnBoot = sharedPrefs.getBoolean("custom_led_on_boot", false)
            val rainbowLedOnBoot = sharedPrefs.getBoolean("rgb_on_boot", false)
            val minFreqOnBoot = sharedPrefs.getBoolean("min_freq_on_boot", false)

            if (rootOnBoot) {
                val rootBeer = RootBeer(context)
                if (!rootBeer.isRooted) {
                    val startIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("auto_root", true)
                    }
                    context.startActivity(startIntent)
                }
            } else if (blockerOnBoot) {
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("start_dns_blocker", true)
                }
                context.startActivity(startIntent)
            }

            // --- LED Boot Logic ---
            val scope = CoroutineScope(Dispatchers.IO)

            if (customLedOnBoot) {
                // If custom color on boot is enabled, run its persistent script
                scope.launch {
                    val r = sharedPrefs.getInt("led_red", 255)
                    val g = sharedPrefs.getInt("led_green", 255)
                    val b = sharedPrefs.getInt("led_blue", 255)
                    val scriptFile = File(context.filesDir, "custom_led.sh")
                    val customColorScript = """
                        #!/system/bin/sh
                        while true; do
                            echo $r > /sys/class/leds/red/brightness
                            echo $g > /sys/class/leds/green/brightness
                            echo $b > /sys/class/leds/blue/brightness
                            sleep 1
                        done
                    """.trimIndent()
                    scriptFile.writeText(customColorScript)
                    RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
                    RootUtils.runAsRoot("${scriptFile.absolutePath} &")
                }
            } else if (rainbowLedOnBoot) {
                // Otherwise, if rainbow on boot is enabled, run its script
                scope.launch {
                    val scriptFile = File(context.filesDir, "rgb_led.sh")
                    scriptFile.writeText(TweakCommands.RGB_SCRIPT)
                    RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
                    RootUtils.runAsRoot("${scriptFile.absolutePath} &")
                }
            }

            // --- CPU Lock Boot Logic ---
            if (minFreqOnBoot) {
                scope.launch {
                    CpuUtils.startMinFreqLock(context)
                }
            }
        }
    }
}