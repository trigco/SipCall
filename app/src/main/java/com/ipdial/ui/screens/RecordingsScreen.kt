package com.ipdial.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.IPDialTopBar
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(vm: SipViewModel, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    
    // Check both internal and external
    val internalDir = File(context.filesDir, "recordings")
    val externalDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "IPCall")

    var recordings by remember { mutableStateOf<List<File>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val iList = if (internalDir.exists()) internalDir.listFiles()?.toList() ?: emptyList() else emptyList()
        val eList = if (externalDir.exists()) externalDir.listFiles()?.toList() ?: emptyList() else emptyList()
        recordings = (iList + eList).sortedByDescending { it.lastModified() }
    }

    var playingFile by remember { mutableStateOf<File?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            IPDialTopBar(accounts = accounts, onOpenDrawer = onOpenDrawer)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (recordings.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No recordings found", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(recordings) { file ->
                        RecordingItem(
                            file = file,
                            isPlaying = playingFile == file,
                            onPlay = {
                                if (playingFile == file) {
                                    mediaPlayer.stop()
                                    mediaPlayer.reset()
                                    playingFile = null
                                } else {
                                    try {
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(file.absolutePath)
                                        mediaPlayer.prepare()
                                        mediaPlayer.start()
                                        playingFile = file
                                        mediaPlayer.setOnCompletionListener { playingFile = null }
                                    } catch (e: Exception) {
                                        playingFile = null
                                        android.widget.Toast.makeText(context, "Playback failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = {
                                file.delete()
                                val iList = if (internalDir.exists()) internalDir.listFiles()?.toList() ?: emptyList() else emptyList()
                                val eList = if (externalDir.exists()) externalDir.listFiles()?.toList() ?: emptyList() else emptyList()
                                recordings = (iList + eList).sortedByDescending { it.lastModified() }
                                if (playingFile == file) {
                                    mediaPlayer.stop()
                                    mediaPlayer.reset()
                                    playingFile = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingItem(file: File, isPlaying: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(file.lastModified()))
    val sizeStr = "%.2f MB".format(file.length().toDouble() / (1024 * 1024))

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onPlay() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("$dateStr • $sizeStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            IconButton(onClick = {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Locate/Share File"))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Error locating file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Folder, "Locate", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
