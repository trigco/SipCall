package com.ipdial.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ipdial.data.model.CallDirection
import com.ipdial.data.model.CallSession
import com.ipdial.data.model.CallState
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.theme.EndRed
import com.ipdial.ui.theme.ForestGreen
import kotlinx.coroutines.delay

@Composable
fun CallScreen(vm: SipViewModel, session: CallSession) {
    val accounts by vm.accounts.collectAsState()
    val contacts by vm.contacts.collectAsState()
    
    val account = accounts.firstOrNull { it.id == session.accountId }
    val simLabel = account?.label?.ifBlank { account.domain } ?: ""

    // Contact matching logic
    val contact = remember(session.remoteUri, contacts) {
        val cleanedSessionUriDigits = vm.cleanUri(session.remoteUri).filter { it.isDigit() }
        if (cleanedSessionUriDigits.length < 10) { // Only attempt contact match for numbers with at least 10 digits
            null
        } else {
            contacts.find { c ->
                c.numbers.any { n ->
                    val cleanedContactNumberDigits = n.filter { it.isDigit() }
                    cleanedContactNumberDigits.length >= 10 && // Contact number must also be long enough
                    (cleanedSessionUriDigits.contains(cleanedContactNumberDigits) || cleanedContactNumberDigits.contains(cleanedSessionUriDigits))
                }
            }
        }
    }
    val displayName = contact?.name ?: session.remoteDisplayName.ifBlank { vm.cleanUri(session.remoteUri) }

    var showDialpad by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    // Call timer
    LaunchedEffect(session.state) {
        if (session.state == CallState.CONFIRMED) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Via label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Calling via $simLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.state == CallState.CONFIRMED) {
                    Text(
                        text = formatDuration(elapsedSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Caller name / number
            Text(
                text = displayName,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = if (displayName.length > 16) 28.sp else 36.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            if (displayName != vm.cleanUri(session.remoteUri)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = vm.cleanUri(session.remoteUri),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // State label (ringing / connecting)
            if (session.state != CallState.CONFIRMED) {
                Spacer(Modifier.height(8.dp))
                PulsingStateLabel(session.state)
            }

            // Avatar circle
            if (contact?.photoUri != null) {
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Call controls ─────────────────────────────────────────────
            AnimatedContent(targetState = showDialpad, label = "dialpad_toggle") { showDp ->
                if (showDp) {
                    InCallDialpad(
                        vm = vm,
                        onHide = { showDialpad = false }
                    )
                } else {
                    CallControls(
                        session = session,
                        isActive = session.state == CallState.CONFIRMED,
                        onKeypad = { showDialpad = true },
                        onMute = { vm.toggleMute() },
                        onSpeaker = { vm.toggleSpeaker() },
                        onHold = { vm.toggleHold() },
                        onRecord = { vm.toggleRecording() }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // End call button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(EndRed)
                    .then(Modifier.clickableNoRipple { vm.hangup() })
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End call",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
fun CallControls(
    session: CallSession,
    isActive: Boolean,
    onKeypad: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onRecord: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CallControlButton(
            icon = Icons.Default.Dialpad,
            label = "Keypad",
            onClick = onKeypad
        )
        CallControlButton(
            icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            label = "Mute",
            active = session.isMuted,
            enabled = isActive,
            onClick = onMute
        )
        CallControlButton(
            icon = if (session.isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
            label = "Speaker",
            active = session.isSpeaker,
            enabled = isActive,
            onClick = onSpeaker
        )
        CallControlButton(
            icon = Icons.Default.RadioButtonChecked,
            label = if (session.isRecording) "Recording" else "Record",
            active = session.isRecording,
            enabled = isActive,
            onClick = onRecord
        )
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .then(if (enabled) Modifier.clickableNoRipple { onClick() } else Modifier)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary
                       else if (!enabled) MaterialTheme.colorScheme.outline
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InCallDialpad(vm: SipViewModel, onHide: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onHide) {
            Text("Hide keypad")
        }
        val keys = listOf(
            "1","2","3","4","5","6","7","8","9","*","0","#"
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            keys.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { digit ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(50))
                                .clickableNoRipple { vm.dialPad(digit[0]) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    digit,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingStateLabel(state: CallState) {
    val label = when (state) {
        CallState.CALLING -> "Calling…"
        CallState.INCOMING -> "Incoming"
        CallState.EARLY -> "Ringing…"
        CallState.CONNECTING -> "Connecting…"
        else -> ""
    }
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
    )
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
