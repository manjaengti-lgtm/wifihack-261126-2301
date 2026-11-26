package com.aibuilt.wifihack.model

import kotlinx.serialization.Serializable

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val level: Int,          // dBm
    val frequency: Int,      // MHz
    val channelWidth: Int = 0,
    val vendor: String = "Unknown"
) {
    val isHidden: Boolean get() = ssid.isBlank()
    val displaySsid: String get() = if (isHidden) "<hidden>" else ssid

    val band: String
        get() = when {
            frequency in 2401..2499 -> "2.4 GHz"
            frequency in 4900..5899 -> "5 GHz"
            frequency in 5900..7125 -> "6 GHz"
            else -> "—"
        }

    val channel: Int
        get() = when {
            frequency == 2484 -> 14
            frequency in 2412..2472 -> (frequency - 2407) / 5
            frequency in 5160..5885 -> (frequency - 5000) / 5
            frequency in 5955..7115 -> (frequency - 5950) / 5
            else -> 0
        }

    val security: String
        get() = when {
            capabilities.contains("WPA3") || capabilities.contains("SAE") -> "WPA3"
            capabilities.contains("RSN") || capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "Open"
        }

    val isOpen: Boolean get() = security == "Open"
    val isWpsLikely: Boolean get() = capabilities.contains("WPS", ignoreCase = true)

    /** 0..4 signal bars from dBm. */
    val signalBars: Int
        get() = when {
            level >= -55 -> 4
            level >= -66 -> 3
            level >= -77 -> 2
            level >= -88 -> 1
            else -> 0
        }
}

@Serializable
data class VaultEntry(
    val id: String,
    val ssid: String,
    val bssid: String,
    val password: String = "",
    val security: String = "",
    val vendor: String = "",
    val note: String = "",
    val savedAt: Long = 0L
)
