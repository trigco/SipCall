package com.ipdial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import com.ipdial.data.model.CallDirection
import com.ipdial.data.model.RegStatus
import com.ipdial.data.model.CallState
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.screens.*
import com.ipdial.ui.theme.IPDialTheme

import androidx.activity.viewModels

object AppState {
    var isForeground = false
}

class MainActivity : ComponentActivity() {

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle results if needed */ }

    private val vm: SipViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        AppState.isForeground = true
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(com.ipdial.service.SipService.NOTIF_ID_INCOMING)
    }

    override fun onPause() {
        super.onPause()
        AppState.isForeground = false
        val session = vm.callSession.value
        if (session != null && session.direction == CallDirection.INCOMING && 
            (session.state == CallState.INCOMING || session.state == CallState.EARLY)) {
            com.ipdial.service.SipService.showIncomingCallNotificationStatic(this, session.remoteDisplayName, session.callId)
        }
    }

    private val testCallNumber = mutableStateOf<String?>(null)
    private val triggerHangup = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        com.ipdial.service.SipService.start(this)

        handleIntent(intent)

        setContent {
            val vm: SipViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val darkMode by vm.darkModeEnabled.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            IPDialTheme(darkTheme = darkMode || systemDark) {
                IPDialApp(testCallNumber, triggerHangup)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null) {
            if (intent.action == "com.ipdial.TEST_CALL") {
                val num = intent.getStringExtra("number")
                if (!num.isNullOrBlank()) {
                    testCallNumber.value = num
                }
            } else if (intent.action == "com.ipdial.TEST_HANGUP") {
                triggerHangup.value = true
            }
        }
    }

    private fun requestRequiredPermissions() {
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
            required.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            required.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            required.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray())
    }
}

sealed class NavDest(val route: String, val label: String, val icon: ImageVector) {
    object Home    : NavDest("home",    "Home",     Icons.Default.Home)
    object Keypad  : NavDest("keypad",  "Keypad",   Icons.Default.Dialpad)
    object Contacts: NavDest("contacts","Contacts", Icons.Default.Contacts)
    object Settings: NavDest("settings","Settings", Icons.Default.Settings)
    object Accounts: NavDest("accounts","Accounts", Icons.Default.AccountBalance)
    object About   : NavDest("about",   "About",    Icons.Default.Info)
    object Recordings: NavDest("recordings", "Recordings", Icons.Default.Mic)
    object Logs    : NavDest("logs",    "Activity Log", Icons.Default.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPDialApp(
    testCallNumber: MutableState<String?> = mutableStateOf(null),
    triggerHangup: MutableState<Boolean> = mutableStateOf(false)
) {
    val vm: SipViewModel = viewModel()
    val callSession by vm.callSession.collectAsState()
    
    LaunchedEffect(testCallNumber.value, triggerHangup.value) {
        testCallNumber.value?.let { number ->
            vm.makeCall(number)
            testCallNumber.value = null
        }
        if (triggerHangup.value) {
            vm.hangup()
            triggerHangup.value = false
        }
    }
    val accounts by vm.accounts.collectAsState()
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val bottomTabs = listOf(NavDest.Home, NavDest.Keypad)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var updateRelease by remember { mutableStateOf<com.ipdial.util.UpdateChecker.GitHubRelease?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            updateRelease = com.ipdial.util.UpdateChecker.checkForUpdates(currentVersion)
        } catch (e: Exception) {}
    }

    if (updateRelease != null) {
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text("Update Available") },
            text = { Text("A new version (${updateRelease?.tag_name}) is available on GitHub. Would you like to download it?\n\n${updateRelease?.body}") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateRelease?.html_url))
                    context.startActivity(intent)
                    updateRelease = null
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updateRelease = null }) { Text("Later") }
            }
        )
    }

    val activeCallSession = callSession

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp),
                        drawerShape = androidx.compose.ui.graphics.RectangleShape
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Menu",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Divider()
                        NavigationDrawerItem(
                            label = { Text("Home") },
                            selected = currentRoute == NavDest.Home.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(NavDest.Home.icon, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Accounts") },
                            selected = currentRoute == NavDest.Accounts.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Accounts.route)
                            },
                            icon = { Icon(NavDest.Accounts.icon, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Recordings") },
                            selected = currentRoute == NavDest.Recordings.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Recordings.route)
                            },
                            icon = { Icon(NavDest.Recordings.icon, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Activity Log") },
                            selected = currentRoute == NavDest.Logs.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Logs.route)
                            },
                            icon = { Icon(NavDest.Logs.icon, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            selected = currentRoute == NavDest.Settings.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Settings.route)
                            },
                            icon = { Icon(NavDest.Settings.icon, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("About") },
                            selected = currentRoute == NavDest.About.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.About.route)
                            },
                            icon = { Icon(NavDest.About.icon, null) }
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    bottomBar = {
                        val showBottomBar = activeCallSession == null && (currentRoute == NavDest.Home.route || currentRoute == NavDest.Keypad.route)
                        if (showBottomBar) {
                            NavigationBar(tonalElevation = 3.dp) {
                                bottomTabs.forEach { dest ->
                                    NavigationBarItem(
                                        selected = currentRoute == dest.route,
                                        onClick = {
                                            navController.navigate(dest.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(dest.icon, dest.label) },
                                        label = { Text(dest.label) },
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (activeCallSession != null) {
                        when {
                            activeCallSession.direction == CallDirection.INCOMING &&
                                    (activeCallSession.state == CallState.INCOMING || 
                                     activeCallSession.state == CallState.EARLY) -> {
                                IncomingCallScreen(vm = vm, session = activeCallSession)
                            }
                            else -> {
                                CallScreen(vm = vm, session = activeCallSession)
                            }
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = NavDest.Home.route,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            composable(NavDest.Home.route) { 
                                HomeScreen(
                                    vm = vm, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                ) 
                            }
                            composable(NavDest.Keypad.route) { 
                                DialpadScreen(
                                    vm = vm, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                ) 
                            }
                            composable(NavDest.Contacts.route) { 
                                ContactsScreen(
                                    vm = vm, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                ) 
                            }
                            composable(NavDest.Settings.route) { 
                                SettingsScreen(
                                    vm = vm, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNavigateToLogs = { navController.navigate(NavDest.Logs.route) }
                                ) 
                            }
                            composable(NavDest.Accounts.route) { 
                                AccountsScreen(
                                    vm = vm, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                ) 
                            }
                            composable(NavDest.Recordings.route) {
                                RecordingsScreen(
                                    vm = vm,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            composable(NavDest.Logs.route) {
                                ActivityLogScreen(
                                    vm = vm,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            composable(NavDest.About.route) { 
                                AboutScreen(
                                    vm = vm,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
