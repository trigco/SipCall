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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ipdial.data.model.*
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar
import com.ipdial.ui.RegStatusIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: SipViewModel, onOpenDrawer: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    var editingAccount by remember { mutableStateOf<SipAccount?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            IPDialTopBar(accounts = accounts, onOpenDrawer = onOpenDrawer)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingAccount = null
                showEditSheet = true
            }) {
                Icon(Icons.Default.Add, "Add Account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // ── Donation ──────────────────────────────────────────────────
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DonationCardSmall(bkashNumber = "01728867695")
                }
            }

            items(accounts) { account ->
                AccountSettingsRow(
                    account = account,
                    onEdit = { editingAccount = account; showEditSheet = true },
                    onDelete = { vm.deleteAccount(account.id) },
                    onSetDefault = { vm.setDefaultAccount(account.id) },
                    onToggleEnabled = { vm.saveAccount(account.copy(isEnabled = !account.isEnabled)) }
                )
            }
        }

        if (showEditSheet) {
            AccountEditSheet(
                existing = editingAccount,
                onSave = { vm.saveAccount(it); showEditSheet = false },
                onDismiss = { showEditSheet = false }
            )
        }
    }
}

@Composable
fun AccountSettingsRow(
    account: SipAccount,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RegStatusIndicator(accounts = listOf(account))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (account.isEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
                    )
                    if (account.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "●",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "${account.username}@${account.domain}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = account.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                modifier = Modifier.size(40.dp, 24.dp)
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Set as default") },
                        onClick = { showMenu = false; onSetDefault() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.padding(start = 40.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditSheet(
    existing: SipAccount?,
    onSave: (SipAccount) -> Unit,
    onDismiss: () -> Unit
) {
    var label     by remember { mutableStateOf(existing?.label ?: "") }
    var username  by remember { mutableStateOf(existing?.username ?: "") }
    var password  by remember { mutableStateOf(existing?.password ?: "") }
    var domain    by remember { mutableStateOf(existing?.domain ?: "") }
    var proxy     by remember { mutableStateOf(existing?.proxy ?: "") }
    var port      by remember { mutableStateOf(existing?.port?.toString() ?: "") }
    var transport by remember { mutableStateOf(existing?.transport ?: Transport.UDP) }
    var codec     by remember { mutableStateOf(existing?.codec ?: PreferredCodec.OPUS) }
    var ecEnabled by remember { mutableStateOf(existing?.ecEnabled ?: true) }
    var nsEnabled by remember { mutableStateOf(existing?.nsEnabled ?: true) }
    var agcEnabled by remember { mutableStateOf(existing?.agcEnabled ?: true) }
    var showPass  by remember { mutableStateOf(false) }

    // Auto-detect transport based on domain/proxy
    LaunchedEffect(domain, proxy) {
        val isSips = domain.startsWith("sips:", ignoreCase = true) || proxy.startsWith("sips:", ignoreCase = true)
        if (isSips && transport != Transport.TLS) {
            transport = Transport.TLS
        } else if (!isSips && transport == Transport.TLS) {
            transport = Transport.UDP
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existing == null) "Add SIP Account" else "Edit Account",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text("Display Name (e.g. Work, Home)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("SIP Username *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password *") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = domain, onValueChange = { domain = it },
                label = { Text("SIP Domain / Server *") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proxy, onValueChange = { proxy = it },
                label = { Text("Outbound Proxy (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                // Transport dropdown
                var transportExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = transportExpanded,
                    onExpandedChange = { transportExpanded = it },
                    modifier = Modifier.weight(1.5f)
                ) {
                    OutlinedTextField(
                        value = transport.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transport") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(transportExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = transportExpanded, onDismissRequest = { transportExpanded = false }) {
                        Transport.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = { transport = t; transportExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank() && domain.isNotBlank()) {
                            onSave(
                                (existing ?: SipAccount()).copy(
                                    label = label,
                                    username = username,
                                    password = password,
                                    domain = domain,
                                    proxy = proxy,
                                    port = port.toIntOrNull(),
                                    transport = transport,
                                    codec = codec,
                                    ecEnabled = ecEnabled,
                                    nsEnabled = nsEnabled,
                                    agcEnabled = agcEnabled
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Register")
                }
            }
        }
    }
}
