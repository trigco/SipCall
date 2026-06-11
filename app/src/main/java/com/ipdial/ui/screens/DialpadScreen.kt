package com.ipdial.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ipdial.data.model.SipAccount
import com.ipdial.data.model.Contact
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar
import com.ipdial.ui.theme.ForestGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(vm: SipViewModel, onOpenDrawer: () -> Unit) {
    val dialString by vm.dialString.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val selectedId by vm.selectedAccountId.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val mostCalled by vm.mostCalledContacts.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val suggestedContacts = remember(dialString, contacts) {
        if (dialString.isBlank()) emptyList()
        else contacts.filter { contact ->
            contact.numbers.any { it.filter { it.isDigit() }.contains(dialString) } ||
            contact.name.contains(dialString, ignoreCase = true)
        }.take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IPDialTopBar(accounts = accounts, onOpenDrawer = onOpenDrawer)

        // Suggested contacts space
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (dialString.isEmpty() && mostCalled.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "Most Called",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(mostCalled) { contact ->
                        SuggestedContactRow(contact) {
                            vm.clearDial()
                            contact.numbers.firstOrNull()?.let { num ->
                                num.filter { it.isDigit() || it == '+' }.forEach { vm.dialPad(it) }
                            }
                        }
                    }
                }
            } else if (suggestedContacts.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(suggestedContacts) { contact ->
                        SuggestedContactRow(contact) {
                            vm.clearDial()
                            contact.numbers.firstOrNull()?.let { num ->
                                num.filter { it.isDigit() || it == '+' }.forEach { vm.dialPad(it) }
                            }
                        }
                    }
                }
            } else if (dialString.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No matching contacts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Account selector (if multiple)
        if (accounts.size > 1) {
            AccountChipRow(accounts, selectedId) { vm.selectAccount(it) }
            Spacer(Modifier.height(4.dp))
        }

        // Dial display row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add to contact") },
                        onClick = { 
                            showMenu = false
                            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, dialString)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Text(
                text = dialString.ifEmpty { "" },
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (dialString.length > 10) 28.sp else 40.sp,
                    fontWeight = FontWeight.Light
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )

            AnimatedVisibility(visible = dialString.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { vm.backspace() },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.clearDial()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (dialString.isEmpty()) Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Keypad grid
        val keys = listOf(
            Triple("1", "⠀", null),
            Triple("2", "ABC", null),
            Triple("3", "DEF", null),
            Triple("4", "GHI", null),
            Triple("5", "JKL", null),
            Triple("6", "MNO", null),
            Triple("7", "PQRS", null),
            Triple("8", "TUV", null),
            Triple("9", "WXYZ", null),
            Triple("*", "", null),
            Triple("0", "+", null),
            Triple("#", "", null),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    row.forEachIndexed { colIndex, (digit, sub, _) ->
                        DialKey(
                            digit = digit,
                            subLabel = sub,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.dialPad(digit[0])
                            },
                            onLongClick = if (digit == "0") {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.dialPad('+')
                                }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        if (colIndex < 2) {
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxHeight().width(1.dp)
                            )
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(180.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(ForestGreen)
                .clickable(enabled = dialString.isNotEmpty()) { 
                    vm.makeCall() 
                }
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SuggestedContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.numbers.firstOrNull() ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialKey(
    digit: String,
    subLabel: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subLabel.isNotBlank()) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountChipRow(
    accounts: List<SipAccount>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        accounts.take(3).forEach { acc ->
            FilterChip(
                selected = acc.id == selectedId,
                onClick = { onSelect(acc.id) },
                label = { Text(acc.label.ifBlank { acc.domain }, fontSize = 10.sp) },
                leadingIcon = if (acc.id == selectedId) {
                    { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
                } else null
            )
        }
    }
}
