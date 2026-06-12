package com.ipdial.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ipdial.data.model.*
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    vm: SipViewModel, 
    onOpenDrawer: () -> Unit,
    onEditBeforeCall: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val accounts  by vm.accounts.collectAsState()
    val callLog   by vm.callLog.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val contactsState by vm.contacts.collectAsState()
    
    var filterIndex by remember { mutableStateOf(0) } // 0: History, 1: Missed, 2: Dialed, 3: Received, 4: Contacts
    val filterLabels = remember { listOf("History", "Missed", "Dialed", "Received", "Contacts") }

    // O(1) map for contact lookup by phone numbers (exact and last 10 digits for suffix matching)
    val contactLookupMap = remember(contactsState) {
        val map = mutableMapOf<String, Contact>()
        contactsState.forEach { contact ->
            contact.numbers.forEach { num ->
                val cleaned = num.filter { it.isDigit() }
                if (cleaned.isNotEmpty()) {
                    map[cleaned] = contact
                    if (cleaned.length >= 10) {
                        map[cleaned.takeLast(10)] = contact
                    }
                }
            }
        }
        map
    }

    val filteredContacts = remember(contactsState, searchQuery, filterIndex) {
        if (filterIndex != 4) emptyList()
        else contactsState.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.numbers.any { num -> num.contains(searchQuery) }
        }
    }

    val filteredLog = remember(callLog, filterIndex, searchQuery) {
        if (filterIndex == 4) return@remember emptyList()
        callLog.filter { entry ->
            val matchesSearch = searchQuery.isBlank() || 
                entry.remoteDisplayName.contains(searchQuery, ignoreCase = true) || 
                entry.remoteUri.contains(searchQuery)
            
            val matchesFilter = when (filterIndex) {
                1 -> entry.missed
                2 -> entry.direction == CallDirection.OUTGOING
                3 -> entry.direction == CallDirection.INCOMING && !entry.missed
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val grouped = remember(filteredLog) {
        filteredLog.groupBy { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestampMs }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
            
            when {
                isSameDay(cal, today) -> "Today"
                isSameDay(cal, yesterday) -> "Yesterday"
                else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(entry.timestampMs))
            }
        }.toList().sortedByDescending { it.second.firstOrNull()?.timestampMs ?: 0L }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IPDialTopBar(accounts = accounts, onOpenDrawer = onOpenDrawer)

        SearchBarRow(
            query = searchQuery,
            onQueryChange = { vm.onSearchQueryChanged(it) }
        )

        FilterChipRow(
            modifier = Modifier.fillMaxWidth(),
            selected = filterIndex,
            onSelectedChange = { filterIndex = it },
            labels = filterLabels
        )

        if (filterIndex == 4) {
            val sortedContacts = remember(filteredContacts) {
                filteredContacts.sortedBy { it.name.trim().lowercase() }
            }
            val alphabet = remember { ('A'..'Z').toList() }
            val letterToFirstIndex = remember(sortedContacts) {
                val map = mutableMapOf<Char, Int>()
                sortedContacts.forEachIndexed { index, contact ->
                    val firstChar = contact.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    val targetChar = if (firstChar in 'A'..'Z') firstChar else '#'
                    if (!map.containsKey(targetChar)) {
                        map[targetChar] = index
                    }
                }
                map
            }

            val contactsListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = contactsListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(sortedContacts, key = { it.id }) { contact ->
                        ContactItem(
                            contact = contact,
                            onCall = { contact.numbers.firstOrNull()?.let { vm.makeCall(it) } },
                            onDetails = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.withAppendedPath(
                                        android.provider.ContactsContract.Contacts.CONTENT_URI, 
                                        contact.id
                                    )
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                AlphabetIndexer(
                    alphabet = alphabet,
                    letterToFirstIndex = letterToFirstIndex,
                    onLetterSelected = { _, index ->
                        coroutineScope.launch {
                            contactsListState.scrollToItem(index)
                        }
                    }
                )
            }
        } else {
            val historyListState = rememberLazyListState()

            LazyColumn(
                state = historyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (grouped.isEmpty() && searchQuery.isBlank()) {
                    item { EmptyLogPrompt() }
                } else {
                    grouped.forEach { (label, entries) ->
                        item {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(entries, key = { it.id }) { entry ->
                            val cleanNumber = cleanUri(entry.remoteUri).filter { it.isDigit() }
                            val contact = remember(cleanNumber, contactLookupMap) {
                                if (cleanNumber.isEmpty()) null
                                else if (cleanNumber.length >= 10) {
                                    contactLookupMap[cleanNumber.takeLast(10)]
                                } else {
                                    contactLookupMap[cleanNumber]
                                }
                            }
                            val numberToCopy = cleanUri(entry.remoteUri).filter { it.isDigit() || it == '+' }
                            CallLogRow(
                                entry   = entry,
                                account = accounts.firstOrNull { it.id == entry.accountId },
                                contact = contact,
                                onCall  = { vm.callBack(entry) },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(numberToCopy))
                                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                },
                                onEdit = { onEditBeforeCall(numberToCopy) },
                                onDelete = { vm.deleteCallLog(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "Search contacts",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun FilterChipRow(
    modifier: Modifier = Modifier,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    labels: List<String>
) {
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelectedChange(index) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (selected == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallLogRow(
    entry: CallLogEntry,
    account: SipAccount?,
    contact: Contact?,
    onCall: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val viaLabel  = account?.label?.ifBlank { account.domain } ?: "SIP"
    val callerName = contact?.name ?: entry.remoteDisplayName.ifBlank { cleanUri(entry.remoteUri) }
    val timeStr   = formatTime(entry.timestampMs)

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onCall,
                    onLongClick = { expanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (contact?.photoUri != null) {
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = (callerName.firstOrNull() ?: '?').uppercaseCharCompat(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (entry.missed)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when {
                            entry.missed                           -> Icons.Default.CallMissed
                            entry.direction == CallDirection.INCOMING -> Icons.Default.CallReceived
                            else                                   -> Icons.Default.CallMade
                        },
                        contentDescription = null,
                        tint = when {
                            entry.missed -> MaterialTheme.colorScheme.error
                            else         -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$viaLabel • $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onCall) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy number") },
                onClick = { expanded = false; onCopy() }
            )
            DropdownMenuItem(
                text = { Text("Edit before call") },
                onClick = { expanded = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

@Composable
fun EmptyLogPrompt() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No recent calls",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Add a SIP account in Settings to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val diffMin = (now - ms) / 60_000
    return when {
        diffMin < 1   -> "Just now"
        diffMin < 60  -> "${diffMin} min ago"
        else          -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
    }
}
