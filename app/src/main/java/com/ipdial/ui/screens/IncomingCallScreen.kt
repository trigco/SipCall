package com.ipdial.ui.screens

import androidx.compose.animation.core.*
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.ipdial.data.model.CallSession
import com.ipdial.ui.SipViewModel
import com.ipdial.ui.theme.EndRed
import com.ipdial.ui.theme.ForestGreen
import kotlin.math.roundToInt

@Composable
fun IncomingCallScreen(vm: SipViewModel, session: CallSession) {
    Log.d("IncomingCallScreen", "Rendering IncomingCallScreen for ${session.remoteUri}")
    val accounts by vm.accounts.collectAsState()
    val contacts by vm.contacts.collectAsState()
    
    val account = accounts.firstOrNull { it.id == session.accountId }
    val viaLine  = account?.label?.ifBlank { account.domain } ?: "SIP"
    
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
    val displayName = contact?.name ?: vm.cleanDisplayName(session.remoteDisplayName, session.remoteUri)

    val callsCardsEnabled by vm.callingCardsEnabled.collectAsState()
    val isFullScreenPhoto = callsCardsEnabled && contact?.photoUri != null
    val textColor = if (isFullScreenPhoto) Color.White else MaterialTheme.colorScheme.onBackground
    val subtitleColor = if (isFullScreenPhoto) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxSize()) {
        if (isFullScreenPhoto) {
            AsyncImage(
                model = contact!!.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(80.dp)) // Increased from 64

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Incoming Call via $viaLine",
                style = MaterialTheme.typography.titleMedium.copy( // Increased from bodyMedium
                    shadow = if (isFullScreenPhoto) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
                ),
                color = subtitleColor,
            )
        }

        Spacer(Modifier.height(40.dp)) // Increased from 32

        Text(
            text = displayName,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.SemiBold, // Increased weight
                fontSize = if (displayName.length > 12) 30.sp else 40.sp,
                shadow = if (isFullScreenPhoto) Shadow(Color.Black, Offset(2f, 2f), 8f) else null
            ),
            textAlign = TextAlign.Center,
            color = textColor,
            modifier = Modifier.padding(horizontal = 24.dp),
            maxLines = 1
        )

        if (displayName != vm.cleanUri(session.remoteUri)) {
            Spacer(Modifier.height(12.dp)) // Increased from 8
            Text(
                text = vm.cleanUri(session.remoteUri),
                style = MaterialTheme.typography.titleLarge.copy( // Increased from bodyMedium
                    shadow = if (isFullScreenPhoto) Shadow(Color.Black, Offset(1f, 1f), 4f) else null
                ),
                color = subtitleColor
            )
        }

        if (!isFullScreenPhoto) {
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
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
                        text = (displayName.firstOrNull() ?: '?').uppercaseCharCompat(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Swipe to Answer/Decline Slider
        var offsetX by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val dragRange = with(density) { 110.dp.toPx() }
        val swipeThreshold = dragRange * 0.7f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The "Pill" Track
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        if (isFullScreenPhoto)
                            Color.White.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
        // Background hints (Decline/Answer icons)
        val infiniteTransition = rememberInfiniteTransition(label = "iconScale")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CallEnd,
                contentDescription = null,
                tint = if (offsetX < -40) EndRed else EndRed.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp).let { if (offsetX <= 0) it.graphicsLayer(scaleX = scale, scaleY = scale) else it }
            )
            Icon(
                Icons.Default.Call,
                contentDescription = null,
                tint = if (offsetX > 40) ForestGreen else ForestGreen.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp).let { if (offsetX >= 0) it.graphicsLayer(scaleX = scale, scaleY = scale) else it }
            )
        }

                val infiniteRipple = rememberInfiniteTransition(label = "ripple")
                val rippleScale by infiniteRipple.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rippleScale"
                )
                val rippleAlpha by infiniteRipple.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rippleAlpha"
                )

                Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
                    if (offsetX == 0f) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .graphicsLayer(scaleX = rippleScale, scaleY = rippleScale, alpha = rippleAlpha)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }

                    // Rounded Phone Icon (Swiping Handle)
                    Box(
                        modifier = Modifier
                            .shadow(elevation = 8.dp, shape = CircleShape)
                            .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                offsetX = (offsetX + delta).coerceIn(-dragRange, dragRange)
                            },
                            onDragStopped = {
                                when {
                                    offsetX >= swipeThreshold -> vm.answerCall()
                                    offsetX <= -swipeThreshold -> vm.hangup()
                                }
                                offsetX = 0f
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            offsetX < -40 -> Icons.Default.CallEnd
                            else -> Icons.Default.Call
                        },
                        contentDescription = null,
                        tint = when {
                            offsetX < -40 -> EndRed
                            else -> ForestGreen
                        },
                        modifier = Modifier.size(36.dp)
                    )
                }
                }
                }
            }
        }
    }
}
