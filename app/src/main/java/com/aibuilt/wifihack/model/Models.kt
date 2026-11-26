package com.aibuilt.wifihack.model

import kotlinx.serialization.Serializable

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String,
    val channelWidth: Int = 0,
    val vendor: String = "Unknown"
) {
    val displaySsid: String get() = if (ssid.isBlank()) "<hidden>" else ssid

    val channel: Int get() = frequencyToChannel(frequency)

    val band: String
        get() = when {
            frequency in 2400..2500 -> "2.4 GHz"
            frequency in 5150..5925 -> "5 GHz"
            frequency in 5926..7125 -> "6 GHz"
            frequency in 4900..5149 -> "5 GHz"
            else -> "?"
        }

    val security: String
        get() = when {
            capabilities.contains("WPA3", true) || capabilities.contains("SAE", true) -> "WPA3"
            capabilities.contains("WPA2", true) || capabilities.contains("RSN", true) -> "WPA2"
            capabilities.contains("WPA", true) -> "WPA"
            capabilities.contains("WEP", true) -> "WEP"
            capabilities.contains("EAP", true) -> "Enterprise"
            else -> "Open"
        }

    val isOpen: Boolean
        get() = !capabilities.contains("WPA", true) &&
            !capabilities.contains("WEP", true) &&
            !capabilities.contains("PSK", true) &&
            !capabilities.contains("EAP", true) &&
            !capabilities.contains("SAE", true)
}

private fun frequencyToChannel(freq: Int): Int = when {
    freq == 2484 -> 14
    freq in 2412..2472 -> (freq - 2407) / 5
    freq in 5150..5925 -> (freq - 5000) / 5
    freq in 4900..5149 -> (freq - 5000) / 5
    freq in 5926..7125 -> (freq - 5950) / 5
    else -> 0
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
