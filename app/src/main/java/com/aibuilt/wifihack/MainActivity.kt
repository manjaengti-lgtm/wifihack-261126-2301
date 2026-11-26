package com.aibuilt.wifihack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aibuilt.wifihack.data.AppContainer
import com.aibuilt.wifihack.root.RootProber
import com.aibuilt.wifihack.root.RootStatus
import com.aibuilt.wifihack.ui.screens.*
import com.aibuilt.wifihack.ui.theme.WifiHackTheme
import kotlinx.coroutines.launch

data class NavDest(val route: String, val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)

        setContent {
            WifiHackTheme {
                var rootStatus by remember { mutableStateOf(RootStatus.CHECKING) }
                LaunchedEffect(Unit) {
                    rootStatus = if (RootProber.isRooted()) RootStatus.ROOTED else RootStatus.NO_ROOT
                }
                AppScaffold(rootStatus)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(rootStatus: RootStatus) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val dests = listOf(
        NavDest("scan", "Scan", Icons.Filled.Wifi),
        NavDest("connect", "Connect", Icons.Filled.Link),
        NavDest("vault", "Vault", Icons.Filled.Lock),
        NavDest("audit", "Audit", Icons.Filled.Security)
    )

    // Permission state.
    var permPrompt by remember { mutableStateOf<String?>(null) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    ModalNavigationDrawerWrapper(navController, dests, rootStatus) { padding, backStack ->
        val current = backStack?.destination?.route ?: "scan"
        NavHost(
            navController = navController,
            startDestination = "scan",
            modifier = Modifier.padding(padding)
        ) {
            composable("scan") {
                ScanScreen(
                    onOpenDetail = { bssid -> navController.navigate("detail/$bssid") }
                )
            }
            composable(
                "detail/{bssid}",
                arguments = listOf(navArgument("bssid") { type = NavType.StringType })
            ) { entry ->
                DetailScreen(
                    bssid = entry.arguments?.getString("bssid").orEmpty(),
                    onConnect = { navController.navigate("connect") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("connect") { ConnectScreen() }
            composable("vault") { VaultScreen() }
            composable("audit") { AuditScreen(rootStatus) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalNavigationDrawerWrapper(
    navController: androidx.navigation.NavHostController,
    dests: List<NavDest>,
    rootStatus: RootStatus,
    content: @Composable (PaddingValues, androidx.navigation.NavBackStackEntry?) -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WifiHack") },
                actions = { RootChip(rootStatus) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val currentRoute = backStack?.destination?.route
                dests.forEach { d ->
                    NavigationBarItem(
                        selected = currentRoute == d.route ||
                            (d.route == "scan" && currentRoute?.startsWith("detail") == true),
                        onClick = {
                            navController.navigate(d.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) }
                    )
                }
            }
        }
    ) { padding -> content(padding, backStack) }
}

@Composable
private fun RootChip(status: RootStatus) {
    val (label, color) = when (status) {
        RootStatus.CHECKING, RootStatus.UNKNOWN -> "root: ?" to MaterialTheme.colorScheme.secondary
        RootStatus.ROOTED -> "root: YES" to MaterialTheme.colorScheme.primary
        RootStatus.NO_ROOT -> "root: NO" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        modifier = Modifier.padding(end = 8.dp)
    )
}
