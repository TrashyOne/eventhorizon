package com.veygax.eventhorizon.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtils {

    suspend fun isRootAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("su -c id")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine()
                process.waitFor()
                reader.close()
                process.destroy()
                output != null && output.contains("uid=0")
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun runAsRoot(command: String, useMountMaster: Boolean = false): String {
        return withContext(Dispatchers.IO) {
            val output = StringBuilder()
            try {
                val suCmd = if (useMountMaster) {
                    arrayOf("su", "--mount-master", "-c", command)
                } else {
                    arrayOf("su", "-c", command)
                }
                val process = Runtime.getRuntime().exec(suCmd)

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                while (errorReader.readLine().also { line = it } != null) {
                    output.append("ERR: ").append(line).append("\n")
                }

                val exitCode = process.waitFor()
                reader.close()
                errorReader.close()
                process.destroy()

                output.append("EXIT:$exitCode\n")
            } catch (e: Exception) {
                return@withContext "Execution failed: ${e.message}"
            }
            output.toString()
        }
    }
}