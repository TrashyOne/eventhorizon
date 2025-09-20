package com.veygax.eventhorizon.core

import android.content.Context
import com.veygax.eventhorizon.utils.RootUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object AppInterceptor {

    private const val SCRIPT_NAME = "interceptor.sh"

    private val INTERCEPTOR_SCRIPT = """
        #!/system/bin/sh
        TIMEOUT=60
        
        # Keep track if we have found and closed each target app
        FOUND_TARGET_CONNECTIONS=0
        FOUND_TARGET_EXPLORE=0

        # Start logcat in the background and get its Process ID (PID)
        logcat -b system | grep --line-buffered "cmp=com.oculus.explore/.ExploreActivity\|cmp=com.oculus.socialplatform/.app.ConnectionsActivity" > /data/local/tmp/interceptor_log.txt &
        LOGCAT_PID=${'$'}!

        # Monitor the output file for changes for the duration of the timeout
        # 'timeout' is a busybox command that will automatically kill the process after T seconds.
        timeout -t ${'$'}TIMEOUT tail -f /data/local/tmp/interceptor_log.txt | while read -r line; do
        
            # Check for Connections app launch, but only if we haven't found it yet
            if [ "${'$'}FOUND_TARGET_CONNECTIONS" -eq 0 ] && echo "${'$'}{'${'$'}line'}" | grep -q "com.oculus.socialplatform/.app.ConnectionsActivity"; then
                am force-stop com.oculus.socialplatform
                echo "Intercepted 'Connections' launch and issued force-stop."
                FOUND_TARGET_CONNECTIONS=1
            fi

            # Check for Explore app launch, but only if we haven't found it yet
            if [ "${'$'}FOUND_TARGET_EXPLORE" -eq 0 ] && echo "${'$'}{'${'$'}line'}" | grep -q "com.oculus.explore/.ExploreActivity"; then
                am force-stop com.oculus.explore
                echo "Intercepted 'Explore' launch and issued force-stop."
                FOUND_TARGET_EXPLORE=1
            fi

            # If we've found and killed both targets, we can exit early.
            if [ "${'$'}FOUND_TARGET_CONNECTIONS" -eq 1 ] && [ "${'$'}FOUND_TARGET_EXPLORE" -eq 1 ]; then
                echo "All target activities intercepted. Exiting."
                # We must kill the background logcat process before exiting
                kill ${'$'}LOGCAT_PID
                exit 0
            fi
        done
        
        # This part runs if the loop finishes (due to timeout)
        echo "Interceptor script timed out or finished. Cleaning up."
        kill ${'$'}LOGCAT_PID
        rm /data/local/tmp/interceptor_log.txt
        exit 0
    """.trimIndent()

    fun start(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val scriptFile = File(context.filesDir, SCRIPT_NAME)
            scriptFile.writeText(INTERCEPTOR_SCRIPT)

            RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
            RootUtils.runAsRoot("${scriptFile.absolutePath} &")
        }
    }

    fun stop() {
        CoroutineScope(Dispatchers.IO).launch {
            RootUtils.runAsRoot("pkill -f $SCRIPT_NAME")
            RootUtils.runAsRoot("pkill logcat") // Failsafe to ensure no orphaned logcat processes
        }
    }
}