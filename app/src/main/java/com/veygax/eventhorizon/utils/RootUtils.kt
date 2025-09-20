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

    suspend fun runAsRoot(command: String): String {
        return withContext(Dispatchers.IO) {
            val output = StringBuilder()
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                os.writeBytes("$command\n")
                os.flush()
                os.writeBytes("exit\n")
                os.flush()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                while (errorReader.readLine().also { line = it } != null) {
                    output.append("ERROR: ").append(line).append("\n")
                }

                process.waitFor()
                os.close()
                reader.close()
                errorReader.close()
                process.destroy()

            } catch (e: Exception) {
                return@withContext "Execution failed: ${e.message}"
            }
            if (output.isBlank()) "Command executed successfully (no output)." else output.toString()
        }
    }
}