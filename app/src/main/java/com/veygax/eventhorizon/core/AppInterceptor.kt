package com.veygax.eventhorizon.core

import android.content.Context
import com.veygax.eventhorizon.utils.RootUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File



/**
 * Persistent script that intercepts and closes Horizon Feed & Social Platform Connections
 */
object AppInterceptor {

    private const val SCRIPT_NAME = "interceptor.sh"

    // The specific Activity components we want to control, as confirmed by dumpsys/logcat.
    private const val TARGET_EXPLORE_ACTIVITY =
        "com.oculus.explore/.ExploreActivity"
    private const val TARGET_CONNECTIONS_ACTIVITY =
        "com.oculus.socialplatform/com.oculus.panelapp.people.PeopleShelfActivity"

    private val INTERCEPTOR_SCRIPT = """
            #!/system/bin/sh

            # Clear logcat buffer so we don't process stale entries
            logcat -c

            # Only capture ActivityTaskManager from now on
            logcat -T 0 ActivityTaskManager:D *:S | while read -r line; do
                case "${'$'}line" in
                    *"START u0"*cmp=${TARGET_EXPLORE_ACTIVITY}*)
                        pm disable "$TARGET_EXPLORE_ACTIVITY"
                        pm enable "$TARGET_EXPLORE_ACTIVITY"
                        ;;
                    *"START u0"*cmp=${TARGET_CONNECTIONS_ACTIVITY}*)
                        pm disable "$TARGET_CONNECTIONS_ACTIVITY"
                        pm enable "$TARGET_CONNECTIONS_ACTIVITY"
                        ;;
                esac
            done
        """.trimIndent()


    /**
     * Starts the persistent watchdog script.
     */
    fun start(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            RootUtils.runAsRoot("pkill -f $SCRIPT_NAME")

            val scriptFile = File(context.filesDir, SCRIPT_NAME)
            scriptFile.writeText(INTERCEPTOR_SCRIPT)

            RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
            RootUtils.runAsRoot("nohup ${scriptFile.absolutePath} > /dev/null 2>&1 &")
        }
    }

    /**
     * Stops the watchdog script.
     */
    fun stop() {
        CoroutineScope(Dispatchers.IO).launch {
            RootUtils.runAsRoot("pkill -f $SCRIPT_NAME")
        }
    }
}