package com.aibuilt.wifihack.model

import kotlinx.serialization.Serializable

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val channel: Int,
    val band: String,
    val security: String,
    val capabilities: String
) {
    val displaySsid: String get() = if (ssid.isBlank()) "<hidden>" else ssid
    val isOpen: Boolean
        get() = !capabilities.contains("WPA", true) &&
            !capabilities.contains("WEP", true) &&
            !capabilities.contains("PSK", true) &&
            !capabilities.contains("EAP", true)
}

@Serializable
data class VaultEntry(
    val id: String,
    val ssid: String,
    val bssid: String,
    val password: String,
    val notes: String,
    val timestamp: Long
)
