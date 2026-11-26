package com.aibuilt.wifihack.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import com.aibuilt.wifihack.model.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed class ConnectResult {
    data class Online(val status: Int, val gateway: String?) : ConnectResult()
    data class Offline(val reason: String, val gateway: String?) : ConnectResult()
    data class Failed(val reason: String) : ConnectResult()
}

class WifiConnector(
    private val context: Context,
    private val client: OkHttpClient,
    private val vault: VaultRepository
) {
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun disconnect() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    suspend fun connectAndVerify(
        ssid: String,
        password: String,
        security: String,
        vendor: String = "",
        saveToVault: Boolean = true
    ): ConnectResult {
        val network: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectQ(ssid, password, security)
        } else {
            connectLegacy(ssid, password, security)
        }

        if (network == null) {
            return ConnectResult.Failed(osFailureReason(security))
        }

        val gateway = gatewayFor(network)
        val (online, code) = verify(network)

        if (online && saveToVault) {
            runCatching {
                vault.add(
VaultEntry(
                        id = UUID.randomUUID().toString(),
                        ssid = ssid,
                        bssid = "",
                        password = password,
                        notes = "Verified online (HTTP $code) · $security · $vendor",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        return if (online) {
            ConnectResult.Online(code, gateway)
        } else {
            ConnectResult.Offline(
                reason = if (code == -1)
                    "Associated but no response from generate_204 (captive portal or no internet)."
                else "Reached internet check but got HTTP $code (portal/blocked).",
                gateway = gateway
            )
        }
    }

    private fun osFailureReason(security: String): String = buildString {
        append("Android refused or timed out the association. Common causes: ")
        append("wrong passphrase, ")
        append(if (security == "Open") "AP out of range, " else "PSK mismatch, ")
        append("AP MAC filtering, or the user dismissed the connection dialog.")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun connectQ(
        ssid: String,
        password: String,
        security: String
    ): Network? = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine { cont ->
            val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
            when (security) {
                "Open" -> { /* no passphrase */ }
                "WPA3" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        specBuilder.setWpa3Passphrase(password)
                else -> specBuilder.setWpa2Passphrase(password)
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specBuilder.build())
                .build()

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (cont.isActive) cont.resume(network)
                }
                override fun onUnavailable() {
                    if (cont.isActive) cont.resume(null)
                }
            }
            callback = cb
            runCatching { cm.requestNetwork(request, cb, 18_000) }
            cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun connectLegacy(
        ssid: String,
        password: String,
        security: String
    ): Network? = withContext(Dispatchers.IO) {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) wm.isWifiEnabled = true

        val conf = android.net.wifi.WifiConfiguration().apply {
            SSID = "\"" + ssid + "\""
            when (security) {
                "Open" -> allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                "WEP" -> {
                    allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                    wepKeys[0] = "\"" + password + "\""
                    wepTxKeyIndex = 0
                }
                else -> preSharedKey = "\"" + password + "\""
            }
        }
        val id = wm.addNetwork(conf)
        if (id == -1) return@withContext null
        wm.disconnect()
        wm.enableNetwork(id, true)
        wm.reconnect()
        delay(6000)
        cm.activeNetwork
    }

    /** Bind an OkHttp call to [net] and hit generate_204. */
    private suspend fun verify(net: Network): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val bound = client.newBuilder()
            .socketFactory(net.socketFactory)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("http://connectivitycheck.gstatic.com/generate_204")
            .head()
            .build()
        try {
            bound.newCall(req).execute().use { r ->
                val ok = r.code == 204 || r.code == 200
                ok to r.code
            }
        } catch (_: Exception) {
            false to -1
        }
    }

    /** Default gateway of [net] read from its LinkProperties routes. */
    private fun gatewayFor(net: Network?): String? {
        net ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        lp.routes.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress?.let { return it }
        lp.linkAddresses.firstOrNull()?.address?.hostAddress?.let { addr ->
            if (addr.contains('.')) return addr.substringBeforeLast('.') + ".1"
        }
        return null
    }

    /** Gateway of the currently active network (used by the admin panel). */
    fun currentGateway(): String? = gatewayFor(cm.activeNetwork)
}
