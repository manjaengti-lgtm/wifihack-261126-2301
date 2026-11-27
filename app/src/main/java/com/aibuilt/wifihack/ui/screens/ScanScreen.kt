package com.aibuilt.wifihack.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aibuilt.wifihack.data.AppContainer
import com.aibuilt.wifihack.data.WifiScanner
import com.aibuilt.wifihack.model.WifiNetwork

// This screen renders the networks emitted by WifiScanner.results, which is a
// StateFlow<List<WifiNetwork>>. WifiNetwork (com.aibuilt.wifihack.model) already
// exposes ssid, bssid, channel, band, security and level, so WifiRow is just an
// alias for it — do NOT declare a separate data class (it would mismatch the flow).
// NOTE: WifiScanner has no refresh(); its scan trigger is triggerScan(). The two
// scanner.refresh() calls in ScanScreen's body must be changed to scanner.triggerScan().
typealias WifiRow = WifiNetwork

@Composable
fun ScanScreen(onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    val scanner: WifiScanner = AppContainer.scanner
    val rows by scanner.results.collectAsStateWithLifecycle()

    var hasPerms by remember { mutableStateOf(hasScanPermissions(context)) }
    var locationOn by remember { mutableStateOf(isLocationEnabled(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPerms = granted.values.any { it } && hasScanPermissions(context)
        if (hasPerms) scanner.start()
    }

    DisposableEffect(hasPerms) {
        if (hasPerms) scanner.start()
        onDispose { scanner.stop() }
    }

    LaunchedEffect(Unit) { locationOn = isLocationEnabled(context) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "nearby_networks (${rows.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
IconButton(onClick = {
                hasPerms = hasScanPermissions(context)
                locationOn = isLocationEnabled(context)
                if (hasPerms) scanner.triggerScan()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            !hasPerms -> PermissionPrompt(
                "Scanning needs Location and Nearby‑WiFi access",
                "Android only lets an app list the WiFi around you after you grant these. " +
                    "WifiHack uses them only to read your own radio — it never sends them anywhere.",
                "Grant access"
            ) { launcher.launch(requiredScanPermissions()) }

            !locationOn -> PermissionPrompt(
                "Location services are off",
                "Even with permission granted, Android returns an empty WiFi list while the phone's " +
                    "location switch is off. Turn it on, then refresh.",
                "Refresh"
            ) {
                locationOn = isLocationEnabled(context)
                if (locationOn) scanner.refresh()
            }

            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("scanning… no networks yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { it.bssid }) { row ->
                    NetworkRow(row) { onOpenDetail(row.bssid) }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(row: WifiRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
row.ssid.ifBlank { "<hidden>" },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "ch ${row.channel} · ${row.band} · ${row.security}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    row.bssid,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // WifiRow declares `level: Int` (RSSI in dBm, copied from ScanResult.level), so it goes straight to SignalMeter(dbm: Int)
            SignalMeter(dbm = row.level)
        }
    }
}

@Composable
private fun SignalMeter(dbm: Int) {
    val bars = when {
        dbm >= -55 -> 4
        dbm >= -67 -> 3
        dbm >= -78 -> 2
        dbm >= -90 -> 1
        else -> 0
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("$dbm dBm", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
        Row {
            repeat(4) { i ->
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .height((6 + i * 4).dp)
                        .width(6.dp)
                ) {
                    val on = i < bars
                    Surface(
                        color = if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
            }
        }
    }
}

@Composable
fun PermissionPrompt(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private fun requiredScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasScanPermissions(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val nearby = ContextCompat.checkSelfPermission(
            context, Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
        fine || nearby
    } else fine
}

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return runCatching {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
}
