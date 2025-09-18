package com.veygax.eventhorizon

import android.content.Context
import android.util.Log
import java.io.File

object CpuUtils {
    const val SCRIPT_NAME = "min_freq_lock.sh" // Renamed for clarity
    private const val TAG = "CpuUtils"

    // Frequencies for the Snapdragon XR2 Gen 2 in the Quest 3
    // Cores 0-3 are LITTLE, Cores 4-6 are big/Prime
    private val LITTLE_CORE_PATHS = (0..3).map { "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_min_freq" }
    private val BIG_CORE_PATHS = (4..6).map { "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_min_freq" }
    
    // Defaulting to the lowest safe frequency you mentioned
    private const val DEFAULT_LITTLE_FREQ = "691200"
    private const val DEFAULT_BIG_FREQ = "691200"

    fun getMinFreqScript(littleFreq: String, bigFreq: String): String {
        val littleCoreCommands = LITTLE_CORE_PATHS.joinToString("\n") { "echo \"$littleFreq\" > $it" }
        val bigCoreCommands = BIG_CORE_PATHS.joinToString("\n") { "echo \"$bigFreq\" > $it" }

        return """
            #!/system/bin/sh
            while true; do
                $littleCoreCommands
                $bigCoreCommands
                sleep 2
            done
        """.trimIndent()
    }

    suspend fun startMinFreqLock(context: Context, littleFreq: String = DEFAULT_LITTLE_FREQ, bigFreq: String = DEFAULT_BIG_FREQ) {
        Log.d(TAG, "Attempting to start Min Freq script...")
        val scriptFile = File(context.filesDir, SCRIPT_NAME)
        val scriptContent = getMinFreqScript(littleFreq, bigFreq)
        scriptFile.writeText(scriptContent)
        RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
        // Ensure only one instance is running
        stopMinFreqLock()
        RootUtils.runAsRoot("${scriptFile.absolutePath} &")
        Log.d(TAG, "Start command issued for Min Freq script.")
    }

    suspend fun stopMinFreqLock() {
        Log.d(TAG, "Attempting to stop Min Freq script...")
        val pid = RootUtils.runAsRoot("pgrep -f $SCRIPT_NAME").trim()
        if (pid.isNotEmpty() && pid.toIntOrNull() != null) {
            Log.d(TAG, "Found running script with PID: $pid. Killing process.")
            RootUtils.runAsRoot("kill $pid")
        } else {
            Log.d(TAG, "No running script found to stop.")
        }
        Log.d(TAG, "Stop command sequence complete.")
    }
}