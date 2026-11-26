package com.aibuilt.wifihack.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.aibuilt.wifihack.model.WifiNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WifiScanner(private val context: Context) {

    private val wm = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val lm = context.applicationContext
        .getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _results = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val results: StateFlow<List<WifiNetwork>> = _results

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var receiver: BroadcastReceiver? = null

    fun wifiEnabled(): Boolean = wm.isWifiEnabled

    fun locationEnabled(): Boolean = runCatching {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    fun hasScanPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            nearby || fine
        } else {
            fine
        }
    }

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                refreshResults()
            }
        }
        receiver = r
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(
            context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    @SuppressLint("MissingPermission")
    fun triggerScan() {
        if (!hasScanPermission()) return
        _scanning.value = true
        runCatching { wm.startScan() }
        refreshResults()
    }

    @SuppressLint("MissingPermission")
    fun refreshResults() {
        if (!hasScanPermission()) {
            _scanning.value = false
            return
        }
        val list = runCatching {
            wm.scanResults.map { sr ->
                @Suppress("DEPRECATION")
                val ssid = sr.SSID ?: ""
                val bssid = sr.BSSID ?: ""
                WifiNetwork(
                    ssid = ssid,
                    bssid = bssid,
                    capabilities = sr.capabilities ?: "",
                    level = sr.level,
                    frequency = sr.frequency,
                    channelWidth = sr.channelWidth,
                    vendor = OuiLookup.vendorForSync(context, bssid)
                )
            }
        }.getOrDefault(emptyList())
            .distinctBy { it.bssid.ifBlank { it.ssid } }
            .sortedByDescending { it.level }
        _results.value = list
        _scanning.value = false
    }
}
