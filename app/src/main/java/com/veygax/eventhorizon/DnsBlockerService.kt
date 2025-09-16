package com.veygax.eventhorizon

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DnsBlockerService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "DnsBlockerService"
        
        const val ACTION_START = "com.veygax.eventhorizon.START_VPN"
        const val ACTION_STOP = "com.veygax.eventhorizon.STOP_VPN"
        private const val BLOCKLIST_URL = "https://raw.githubusercontent.com/Lumince/eventhorizon/refs/heads/main/hosts"
        
        private val blocklist = ConcurrentHashMap.newKeySet<String>()
        var isRunning = false

        fun isDomainBlocked(domain: String): Boolean {
            return blocklist.contains(domain)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    Log.d(TAG, "Starting VPN service...")
                    serviceScope.launch {
                        loadBlocklist()
                        startVpn()
                    }
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping VPN service...")
                stopVpn()
            }
        }
        return START_STICKY
    }

    private suspend fun loadBlocklist() {
        Log.d(TAG, "Loading blocklist from URL...")
        try {
            val lines = URL(BLOCKLIST_URL).readText().lines()
            blocklist.clear()
            lines.forEach { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    blocklist.add(parts[1].trim())
                }
            }
            Log.d(TAG, "Blocklist loaded successfully with ${blocklist.size} entries.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blocklist", e)
        }
    }

    private fun startVpn() {
        if (vpnInterface != null) {
            Log.d(TAG, "VPN already started.")
            return
        }
        Log.d(TAG, "Establishing VPN tunnel...")
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            // FIX: Add a local address for the VPN interface
            .addAddress("10.0.0.2", 24) 
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()
        if (vpnInterface != null) {
            Log.d(TAG, "VPN tunnel established successfully.")
        } else {
            Log.e(TAG, "Failed to establish VPN tunnel.")
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        serviceJob.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
        Log.d(TAG, "VPN service stopped.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called.")
        stopVpn()
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.w(TAG, "VPN revoked by system!")
        stopVpn()
    }
}