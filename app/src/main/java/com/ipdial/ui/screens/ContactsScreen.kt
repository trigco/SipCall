package com.ipdial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ipdial.data.model.Contact
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar

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

    Scaffold(
        topBar = {
            IPDialTopBar(accounts = accounts, onOpenDrawer = onOpenDrawer)
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts) { contact ->
                    ContactItem(
                        contact = contact,
                        onCall = {
                            contact.numbers.firstOrNull()?.let { num ->
                                vm.makeCall(num)
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
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onCall: () -> Unit, onDetails: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetails() }
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
            Text(contact.numbers.firstOrNull() ?: "", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Default.Call, "Call", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
