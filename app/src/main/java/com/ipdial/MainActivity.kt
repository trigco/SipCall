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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
import com.ipdial.data.model.CallState
import com.ipdial.data.model.CallSession
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.screens.*
import com.ipdial.ui.theme.IPDialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow

import androidx.activity.viewModels

import androidx.activity.enableEdgeToEdge

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        requestRequiredPermissions()
        com.ipdial.service.SipService.start(this)

        handleIntent(intent)

        setContent {
            val vm: SipViewModel = viewModel()
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
    val callingCardsEnabled by vm.callingCardsEnabled.collectAsState()
    var showFullIncomingScreen by remember { mutableStateOf(false) }

    LaunchedEffect(callSession, callingCardsEnabled) {
        val session = callSession
        if (session == null) {
            showFullIncomingScreen = false
        } else if (session.direction == CallDirection.INCOMING && 
                   (session.state == CallState.INCOMING || session.state == CallState.EARLY)) {
            if (callingCardsEnabled) {
                showFullIncomingScreen = true
            } else {
                showFullIncomingScreen = false
                kotlinx.coroutines.delay(5000)
                showFullIncomingScreen = true
            }
        } else {
            showFullIncomingScreen = true
        }
    }
    
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
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            updateRelease = com.ipdial.util.UpdateChecker.checkForUpdates(currentVersion)
        } catch (_: Exception) {}
    }

    if (updateRelease != null) {
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text("Update Available") },
            text = { Text("A new version (${updateRelease?.tagName}) is available on GitHub. Would you like to download it?\n\n${updateRelease?.body}") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateRelease?.htmlUrl))
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Menu",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleLarge
                        )
                        HorizontalDivider()
                        NavigationDrawerItem(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(44.dp),
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(44.dp),
                            label = { Text("Accounts") },
                            selected = currentRoute == NavDest.Accounts.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Accounts.route)
                            },
                            icon = { Icon(NavDest.Accounts.icon, null) }
                        )
                        NavigationDrawerItem(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(44.dp),
                            label = { Text("Recordings") },
                            selected = currentRoute == NavDest.Recordings.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Recordings.route)
                            },
                            icon = { Icon(NavDest.Recordings.icon, null) }
                        )

                        NavigationDrawerItem(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(44.dp),
                            label = { Text("Settings") },
                            selected = currentRoute == NavDest.Settings.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(NavDest.Settings.route)
                            },
                            icon = { Icon(NavDest.Settings.icon, null) }
                        )
                        NavigationDrawerItem(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(44.dp),
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
                    contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
                    bottomBar = {
                        val showBottomBar = (activeCallSession == null || !showFullIncomingScreen) && (currentRoute == NavDest.Home.route || currentRoute == NavDest.Keypad.route)
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
                    if (activeCallSession != null && showFullIncomingScreen) {
                        when (activeCallSession.direction) {
                            CallDirection.INCOMING -> {
                                if (activeCallSession.state == CallState.INCOMING || 
                                     activeCallSession.state == CallState.EARLY) {
                                    IncomingCallScreen(vm = vm, session = activeCallSession)
                                } else {
                                    CallScreen(vm = vm, session = activeCallSession)
                                }
                            }
                            else -> {
                                CallScreen(vm = vm, session = activeCallSession)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onEditBeforeCall = { number ->
                                            vm.prefillDialer(number)
                                            navController.navigate(NavDest.Keypad.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
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

                            // If there is an incoming call, but we are not showing full screen, show the banner!
                            if (activeCallSession != null && 
                                activeCallSession.direction == CallDirection.INCOMING &&
                                (activeCallSession.state == CallState.INCOMING || activeCallSession.state == CallState.EARLY)) {
                                
                                val contacts by vm.contacts.collectAsState()
                                val contact = remember(activeCallSession.remoteUri, contacts) {
                                    val cleanedSessionUriDigits = vm.cleanUri(activeCallSession.remoteUri).filter { it.isDigit() }
                                    if (cleanedSessionUriDigits.length < 10) {
                                        null
                                    } else {
                                        contacts.find { c ->
                                            c.numbers.any { n ->
                                                val cleanedContactNumberDigits = n.filter { it.isDigit() }
                                                cleanedContactNumberDigits.length >= 10 &&
                                                (cleanedSessionUriDigits.contains(cleanedContactNumberDigits) || cleanedContactNumberDigits.contains(cleanedSessionUriDigits))
                                            }
                                        }
                                    }
                                }
                                val displayName = contact?.name ?: activeCallSession.remoteDisplayName.ifBlank { vm.cleanUri(activeCallSession.remoteUri) }

                                IncomingCallBanner(
                                    session = activeCallSession,
                                    displayName = displayName,
                                    onAnswer = { vm.answerCall() },
                                    onDecline = { vm.hangup() },
                                    onClick = { showFullIncomingScreen = true }
                                )
                            }
                            
                            val showAd by vm.showAd.collectAsState()
                            if (showAd) {
                                AdDialog(onDismiss = { vm.dismissAd() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdDialog(onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 0.dp, vertical = 24.dp)
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(20.dp).padding(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = "Close Ad",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(90.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                webViewClient = WebViewClient()
                                val html = """
                                    <html>
                                    <body style="margin:0;padding:0;display:flex;justify-content:center;align-items:center;background-color:transparent;">
                                        <script>
                                          atOptions = {
                                            'key' : '408102d569914168e5792b69e28d7e6d',
                                            'format' : 'iframe',
                                            'height' : 90,
                                            'width' : 728,
                                            'params' : {}
                                          };
                                        </script>
                                        <script src="https://www.highperformanceformat.com/408102d569914168e5792b69e28d7e6d/invoke.js"></script>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://www.highperformanceformat.com", html, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun IncomingCallBanner(
    session: CallSession,
    displayName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .statusBarsPadding()
            .shadow(8.dp, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Incoming Call",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDecline,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = com.ipdial.ui.theme.EndRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Decline",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onAnswer,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = com.ipdial.ui.theme.ForestGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Answer",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
