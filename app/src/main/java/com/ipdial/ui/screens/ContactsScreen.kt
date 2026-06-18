package com.ipdial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ipdial.data.model.Contact
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar
import com.ipdial.ui.NumberPickerDialog

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(vm: SipViewModel, onOpenDrawer: () -> Unit) {
    val contacts by vm.contacts.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val context = LocalContext.current
    var activeContactForNumberPicker by remember { mutableStateOf<Contact?>(null) }

    val sortedContacts = remember(contacts) {
        contacts.sortedBy { it.name.trim().lowercase() }
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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            IPDialTopBar(accounts = accounts, vm = vm, onOpenDrawer = onOpenDrawer)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = CircleShape
            )
            
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(sortedContacts, key = { it.id }) { contact ->
                        ContactItem(
                            contact = contact,
                            onCall = {
                                if (contact.numbers.size > 1) {
                                    activeContactForNumberPicker = contact
                                } else {
                                    contact.numbers.firstOrNull()?.let { num ->
                                        vm.makeCall(num)
                                    }
                                }
                            },
                            onDetails = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
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
                            listState.scrollToItem(index)
                        }
                    }
                )
            }
        }
    }

    activeContactForNumberPicker?.let { contact ->
        NumberPickerDialog(
            numbers = contact.numbers,
            onPick = { number -> vm.makeCall(number) },
            onDismiss = { activeContactForNumberPicker = null }
        )
    }
}

@Composable
fun ContactItem(contact: Contact, onCall: () -> Unit, onDetails: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithRipple { onDetails() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (contact.photoUri != null) {
                AsyncImage(model = contact.photoUri, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(contact.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            contact.numbers.forEach { number ->
                Text(number, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Default.Call, "Call", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
