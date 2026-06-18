package com.ipdial.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ipdial.data.model.*
import com.ipdial.ui.IPDialTopBar
import com.ipdial.ui.SipViewModel
import com.ipdial.util.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SipViewModel, onOpenDrawer: () -> Unit, onNavigateToLogs: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsState()
    val globalRingtone by vm.globalRingtone.collectAsState()
    val activeAccount by vm.activeAccount.collectAsState()
    
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch { vm.repo.setGlobalRingtone(uri?.toString()) }
        }
    }

    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }
        catch (e: Exception) { "1.0" }
    }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<UpdateChecker.GitHubRelease?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showCodecDialog by remember { mutableStateOf(false) }

    if (showUpdateDialog && updateRelease != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, null) },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Version ${updateRelease!!.tagName.trimStart('v')} is available.")
                    if (updateRelease!!.body.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(updateRelease!!.body.take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateRelease!!.htmlUrl))
                    context.startActivity(intent)
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
            }
        )
    }

    if (showCodecDialog && activeAccount != null) {
        AlertDialog(
            onDismissRequest = { showCodecDialog = false },
            title = { Text("Select Preferred Codec") },
            text = {
                Column {
                    PreferredCodec.values().forEach { codec ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val updated = activeAccount!!.copy(codec = codec)
                                vm.saveAccount(updated)
                                showCodecDialog = false
                                vm.dismissAd()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = activeAccount?.codec == codec, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(codec.name)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            IPDialTopBar(accounts = accounts, vm = vm, onOpenDrawer = onOpenDrawer)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DonationCardSmall(bkashNumber = "01728867695")
                }
            }

            item { SettingsSection("Updates") }
            item {
                SettingsRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = if (checkingUpdate) "Checking…" else "Current version: $currentVersion",
                    trailing = { if (checkingUpdate) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) },
                    onClick = {
                        if (!checkingUpdate) {
                            checkingUpdate = true
                            scope.launch {
                                val release = UpdateChecker.checkForUpdates(currentVersion)
                                checkingUpdate = false
                                if (release != null) {
                                    updateRelease = release
                                    showUpdateDialog = true
                                } else {
                                    android.widget.Toast.makeText(context, "You're on the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }

            item { SettingsSection("Audio") }
            item {
                val ringtoneName = if (globalRingtone != null) {
                    try { RingtoneManager.getRingtone(context, Uri.parse(globalRingtone)).getTitle(context) }
                    catch (_: Exception) { "Default" }
                } else "Default"
                
                SettingsRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Global Ringtone",
                    subtitle = ringtoneName,
                    onClick = { 
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Global Ringtone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, globalRingtone?.let { Uri.parse(it) })
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        }
                        ringtonePickerLauncher.launch(intent)
                    }
                )
            }

            item {
                val globalVibrate by vm.globalVibrate.collectAsState()
                SettingsRow(
                    icon = Icons.Default.Vibration,
                    title = "Vibrate on Ring",
                    subtitle = "Vibrate when receiving incoming calls",
                    trailing = { Switch(checked = globalVibrate, onCheckedChange = { vm.setGlobalVibrate(it) }) },
                    onClick = { vm.setGlobalVibrate(!globalVibrate) }
                )
            }

            item { SettingsSection("Audio Codecs") }
            item {
                SettingsRow(
                    icon = Icons.Default.Audiotrack,
                    title = "Preferred Codec",
                    subtitle = activeAccount?.codec?.name ?: "Select Codec",
                    onClick = {
                        vm.onCodecAction(context)
                        if (activeAccount != null) {
                            showCodecDialog = true
                        } else {
                            android.widget.Toast.makeText(context, "No active account", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item { SettingsSection("General") }
            item {
                val callsCardsEnabled by vm.callingCardsEnabled.collectAsState()
                SettingsRow(
                    icon = Icons.Default.ContactPage,
                    title = "Calling Cards",
                    subtitle = "Enable full screen contact photo setup",
                    trailing = { Switch(checked = callsCardsEnabled, onCheckedChange = { vm.setCallingCards(it) }) },
                    onClick = { vm.setCallingCards(!callsCardsEnabled) }
                )
            }

            item {
                val darkModeEnabled by vm.darkModeEnabled.collectAsState()
                SettingsRow(
                    icon = Icons.Default.DisplaySettings,
                    title = "Dark Mode",
                    subtitle = "Dark mode",
                    trailing = { Switch(checked = darkModeEnabled, onCheckedChange = { vm.setDarkMode(it) }) },
                    onClick = { vm.setDarkMode(!darkModeEnabled) }
                )
            }
            
            item {
                val dndEnabled by vm.dndEnabled.collectAsState()
                SettingsRow(
                    icon = Icons.Default.DoNotDisturbOn,
                    title = "Do Not Disturb",
                    subtitle = "Automatically decline incoming calls",
                    trailing = { Switch(checked = dndEnabled, onCheckedChange = { vm.setDnd(it) }) },
                    onClick = { vm.setDnd(!dndEnabled) }
                )
            }

            item { SettingsSection("System") }
            item {
                SettingsRow(
                    icon = Icons.Default.List,
                    title = "Activity Log",
                    subtitle = "View full system activity logs",
                    onClick = { onNavigateToLogs() }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp, end = 16.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithRipple { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.padding(start = 56.dp)
        )
    }
}
