package com.ipdial.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ipdial.R
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: SipViewModel, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()

    val appIconBitmap = remember(context) {
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 200
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 200
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            IPDialTopBar(accounts = accounts, onOpenDrawer = onOpenDrawer)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(100.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "IPDial",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "A simple VoIP client for Android.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Developer:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "NAZIM U. NAEEM",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "facebook.com/nazimunaeem1",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com/nazimunaeem1"))
                    context.startActivity(intent)
                }
            )
            Text(
                text = "Telegram: t.me/IPDial",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/IPDial"))
                    context.startActivity(intent)
                }
            )
            
            Spacer(Modifier.height(48.dp))
            
            DonationCardLarge(bkashNumber = "01728867695")
        }
    }
}

@Composable
fun DonationCardLarge(bkashNumber: String) {
    val clipboardManager = LocalClipboardManager.current
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Fuelling innovation. Support the development and help us keep the lines open!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Donate via bKash: $bkashNumber",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { clipboardManager?.setText(AnnotatedString(bkashNumber)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
