package com.veygax.eventhorizon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object AppInstaller {

    suspend fun downloadAndInstall(
        context: Context,
        owner: String,
        repo: String,
        onStatusUpdate: (String) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Find the latest APK download URL from GitHub API
                onStatusUpdate("Finding latest release...")
                val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val jsonText = URL(apiUrl).readText()
                val json = JSONObject(jsonText)
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val fileName = asset.getString("name")
                    if (fileName.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (apkUrl == null) {
                    onStatusUpdate("Error: No APK found in the latest release.")
                    return@withContext false
                }

                // Step 2: Download the APK
                onStatusUpdate("Downloading $repo...")
                val apkFile = File(context.cacheDir, "$repo.apk")
                URL(apkUrl).openStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Step 3: Install the APK using root
                onStatusUpdate("Installing...")
                // The -r flag allows reinstalling/updating the app
                val result = RootUtils.runAsRoot("pm install -r \"${apkFile.absolutePath}\"")

                // Step 4: Cleanup
                apkFile.delete()

                if (result.contains("Success")) {
                    onStatusUpdate("$repo installed successfully!")
                    return@withContext true
                } else {
                    onStatusUpdate("Installation failed. Result:\n$result")
                    return@withContext false
                }

            } catch (e: Exception) {
                onStatusUpdate("An error occurred: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
    }
}