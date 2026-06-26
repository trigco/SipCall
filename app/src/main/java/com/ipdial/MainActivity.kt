package com.ipdial

import android.app.KeyguardManager
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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.ipdial.data.model.*
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.screens.*
import com.ipdial.ui.theme.IPDialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow

import androidx.activity.viewModels

import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalView

object AppState {
    var isForeground = false
}

class MainActivity : ComponentActivity() {

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.READ_CONTACTS] == true) {
            vm.refreshContacts()
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        applyLockScreenFlags()
        
        requestRequiredPermissions()
        com.ipdial.service.SipService.start(this)

        handleIntent(intent)

        setContent {
            val vm: SipViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()
            val fontMultiplier by vm.fontSizeMultiplier.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                else -> systemDark
            }

            // Keep screen on when there's an active call
            val callSession by vm.callSession.collectAsState()
            val localView = LocalView.current
            LaunchedEffect(callSession) {
                val window = (localView.context as? android.app.Activity)?.window
                if (callSession != null) {
                    // Screen on during active or incoming call
                    window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        (localView.context as? android.app.Activity)?.setTurnScreenOn(true)
                        (localView.context as? android.app.Activity)?.setShowWhenLocked(true)
                    }
                } else {
                    // Allow screen to turn off after call ends
                    window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            IPDialTheme(
                darkTheme = darkTheme,
                fontMultiplier = fontMultiplier
            ) {
                IPDialApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyLockScreenFlags()
        handleIntent(intent)
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let { i ->
            if (i.action == "com.ipdial.TEST_CALL") {
                val num = i.getStringExtra("number")
                if (!num.isNullOrBlank()) {
                    vm.makeCall(num)
                }
            } else if (i.action == "com.ipdial.TEST_HANGUP") {
                vm.hangup()
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
    object Logs    : NavDest("logs",    "Activity Log", Icons.AutoMirrored.Filled.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPDialApp() {
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
            showFullIncomingScreen = true
        } else {
            showFullIncomingScreen = true
        }
    }
    
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: NavDest.Home.route

    // Navigation drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    UpdateCheckDialog()

    // Wrap the entire app in Ltr by default, but ModalNavigationDrawer uses LocalLayoutDirection
    // to decide which side it opens from. We want it to open from the right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // Wrap drawer content back to Ltr so text isn't flipped
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AppDrawerSheet(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            scope.launch { drawerState.close() }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) {
            // Wrap main app content back to Ltr
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                AppScaffold(
                    vm = vm,
                    navController = navController,
                    currentRoute = currentRoute,
                    callSession = callSession,
                    showFullIncomingScreen = showFullIncomingScreen,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onShowFullIncoming = { showFullIncomingScreen = true }
                )
            }
        }
    }
}

@Composable
fun UpdateCheckDialog() {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerSheet(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        NavDest.Home,
        NavDest.Accounts,
        NavDest.Recordings,
        NavDest.Settings,
        NavDest.About
    )

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
        items.forEach { dest ->
            NavigationDrawerItem(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(44.dp),
                label = { Text(dest.label) },
                selected = currentRoute == dest.route,
                onClick = { onNavigate(dest.route) },
                icon = { Icon(dest.icon, null) }
            )
        }
    }
}

@Composable
fun AppScaffold(
    vm: SipViewModel,
    navController: androidx.navigation.NavHostController,
    currentRoute: String,
    callSession: CallSession?,
    showFullIncomingScreen: Boolean,
    onOpenDrawer: () -> Unit,
    onShowFullIncoming: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
        bottomBar = {
            AppBottomBar(navController, currentRoute, callSession, showFullIncomingScreen)
        }
    ) { innerPadding ->
        AppMainContent(
            vm = vm,
            navController = navController,
            innerPadding = innerPadding,
            callSession = callSession,
            showFullIncomingScreen = showFullIncomingScreen,
            onOpenDrawer = onOpenDrawer,
            onShowFullIncoming = onShowFullIncoming
        )
    }
}

@Composable
fun AppBottomBar(
    navController: androidx.navigation.NavHostController,
    currentRoute: String,
    callSession: CallSession?,
    showFullIncomingScreen: Boolean
) {
    val bottomTabs = listOf(NavDest.Home, NavDest.Keypad)
    val showBottomBar = (callSession == null || !showFullIncomingScreen) && 
                        (currentRoute == NavDest.Home.route || currentRoute == NavDest.Keypad.route)
    
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

@Composable
fun AppMainContent(
    vm: SipViewModel,
    navController: androidx.navigation.NavHostController,
    innerPadding: PaddingValues,
    callSession: CallSession?,
    showFullIncomingScreen: Boolean,
    onOpenDrawer: () -> Unit,
    onShowFullIncoming: () -> Unit
) {
    if (callSession != null && showFullIncomingScreen) {
        CallOverlay(vm, callSession)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost(vm, navController, innerPadding, onOpenDrawer)
            
            IncomingCallBannerOverlay(vm, callSession, onShowFullIncoming)
        }
    }
}

@Composable
fun CallOverlay(vm: SipViewModel, session: CallSession) {
    when (session.direction) {
        CallDirection.INCOMING -> {
            if (session.state == CallState.INCOMING || session.state == CallState.EARLY) {
                IncomingCallScreen(vm = vm, session = session)
            } else {
                CallScreen(vm = vm, session = session)
            }
        }
        else -> {
            CallScreen(vm = vm, session = session)
        }
    }
}

@Composable
fun AppNavHost(
    vm: SipViewModel,
    navController: androidx.navigation.NavHostController,
    innerPadding: PaddingValues,
    onOpenDrawer: () -> Unit
) {
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
                onOpenDrawer = onOpenDrawer,
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
            DialpadScreen(vm = vm, onOpenDrawer = onOpenDrawer) 
        }
        composable(NavDest.Contacts.route) { 
            ContactsScreen(vm = vm, onOpenDrawer = onOpenDrawer) 
        }
        composable(NavDest.Settings.route) { 
            SettingsScreen(
                vm = vm, 
                onOpenDrawer = onOpenDrawer,
                onNavigateToLogs = { navController.navigate(NavDest.Logs.route) }
            ) 
        }
        composable(NavDest.Accounts.route) { 
            AccountsScreen(vm = vm, onOpenDrawer = onOpenDrawer) 
        }
        composable(NavDest.Recordings.route) {
            RecordingsScreen(vm = vm, onOpenDrawer = onOpenDrawer)
        }
        composable(NavDest.Logs.route) {
            ActivityLogScreen(vm = vm, onOpenDrawer = onOpenDrawer)
        }
        composable(NavDest.About.route) { 
            AboutScreen(vm = vm, onOpenDrawer = onOpenDrawer)
        }
    }
}

@Composable
fun IncomingCallBannerOverlay(
    vm: SipViewModel,
    callSession: CallSession?,
    onShowFullIncoming: () -> Unit
) {
    if (callSession != null && 
        callSession.direction == CallDirection.INCOMING &&
        (callSession.state == CallState.INCOMING || callSession.state == CallState.EARLY)) {
        
        val contacts by vm.contacts.collectAsState()
        val contact = remember(callSession.remoteUri, contacts) {
            val cleanedSessionUriDigits = vm.cleanUri(callSession.remoteUri).filter { it.isDigit() }
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
        val displayName = contact?.name ?: callSession.remoteDisplayName.ifBlank { vm.cleanUri(callSession.remoteUri) }

        IncomingCallBanner(
            displayName = displayName,
            onAnswer = { vm.answerCall() },
            onDecline = { vm.hangup() },
            onClick = onShowFullIncoming
        )
    }
}

@Composable
fun AdDialog(onDismiss: () -> Unit) {
    // AdDialog removed
}

@Composable
fun IncomingCallBanner(
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
