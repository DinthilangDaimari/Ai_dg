package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.OutboundCallStatus
import com.example.data.model.OutboundCallSummary
import com.example.data.model.TranscriptEvent
import com.example.ui.navigation.Screen
import com.example.ui.theme.DinthiLime
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundCallScreen(
    sessionId: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.activeOutboundSession.collectAsStateWithLifecycle()
    val isMuted by viewModel.isOutboundMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isOutboundSpeakerOn.collectAsStateWithLifecycle()

    val currentStatus = session?.status ?: OutboundCallStatus.PREPARING.name
    val isTerminal = currentStatus == OutboundCallStatus.COMPLETED.name ||
        currentStatus == OutboundCallStatus.FAILED.name

    var secondsElapsed by remember(sessionId) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    BackHandler {
        if (!isTerminal) {
            viewModel.endOutboundCall("user_navigated_back")
        }
        viewModel.navigateBack()
    }

    LaunchedEffect(sessionId) {
        if (session?.id != sessionId) {
            viewModel.refreshOutboundSession(sessionId)
        }
    }

    LaunchedEffect(session?.durationSeconds, currentStatus) {
        val serverSecs = session?.durationSeconds ?: 0
        if (serverSecs > secondsElapsed) {
            secondsElapsed = serverSecs
        }
        if (currentStatus == OutboundCallStatus.CONNECTED.name ||
            currentStatus == OutboundCallStatus.AI_SPEAKING.name ||
            currentStatus == OutboundCallStatus.LISTENING.name ||
            currentStatus == OutboundCallStatus.PROCESSING.name
        ) {
            while (true) {
                delay(1000)
                secondsElapsed++
            }
        }
    }

    LaunchedEffect(session?.transcript?.size) {
        if ((session?.transcript?.size ?: 0) > 0) {
            listState.animateScrollToItem(session!!.transcript.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session?.recipientName ?: "Outbound Call",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = session?.phoneNumber ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isTerminal) {
                            viewModel.endOutboundCall("user_navigated_back")
                        }
                        viewModel.navigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTerminal) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            DinthiLime.copy(alpha = 0.15f)
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = formatDuration(session?.durationSeconds?.takeIf { isTerminal } ?: secondsElapsed),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isTerminal) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                DinthiLime
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CallStatusHeaderCard(
                    status = currentStatus,
                    mode = session?.mode ?: "AI"
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = DinthiLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Goal: ${session?.goal ?: "General"} • Dinthi AI always identifies as an AI assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    val events = session?.transcript ?: emptyList()
                    if (events.isEmpty() && !isTerminal) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (session?.mode == "HUMAN") {
                                        "Device dialer active. Tap Finish & Log once your call concludes."
                                    } else {
                                        "Waiting for actual speech events from Dinthi Secure Backend..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    items(events, key = { it.id }) { event ->
                        TranscriptBubble(
                            event = event,
                            recipientLabel = session?.recipientName ?: "Recipient"
                        )
                    }

                    if (currentStatus == OutboundCallStatus.FAILED.name) {
                        item {
                            FailedCallGuidanceCard(
                                reason = session?.failureReason
                                    ?: "NOT CONFIGURED: Telephony provider is not configured on the secure backend.",
                                onOpenProviderSetup = { viewModel.navigateTo(Screen.TelephonyProviderSetup) },
                                onOpenSimulator = { viewModel.navigateTo(Screen.CallSimulator) }
                            )
                        }
                    }

                    if (currentStatus == OutboundCallStatus.COMPLETED.name && session?.summary != null) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            PostCallSummaryCard(summary = session!!.summary!!)
                        }
                    }
                }

                if (!isTerminal) {
                    if (session?.mode == "HUMAN") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DinthiLime.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = DinthiLime)
                                Text(
                                    text = "Direct call handed off to your device phone dialer. Tap Finish below when your call ends to save this record.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleOutboundMute() },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleOutboundSpeaker() },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSpeakerOn) DinthiLime.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) DinthiLime else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                val endReason = if (session?.mode == "HUMAN") "human_call_completed" else "user_hung_up"
                                viewModel.endOutboundCall(endReason)
                            },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("end_outbound_call_button"),
                            shape = RoundedCornerShape(27.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (session?.mode == "HUMAN") "Finish & Log" else "End Call",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Dashboard) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(bottom = 12.dp)
                            .testTag("return_to_dashboard_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                    ) {
                        Text("Return to Dashboard", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallStatusHeaderCard(
    status: String,
    mode: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val isTerminal = status == OutboundCallStatus.COMPLETED.name ||
        status == OutboundCallStatus.FAILED.name

    val (badgeColor, statusLabel) = when (status) {
        OutboundCallStatus.PREPARING.name -> Color(0xFFF59E0B) to "Preparing"
        OutboundCallStatus.CALLING.name -> Color(0xFF3B82F6) to "Calling"
        OutboundCallStatus.RINGING.name -> Color(0xFF3B82F6) to "Ringing"
        OutboundCallStatus.CONNECTED.name -> DinthiLime to "Connected"
        OutboundCallStatus.AI_SPEAKING.name -> DinthiLime to "Dinthi speaking"
        OutboundCallStatus.LISTENING.name -> Color(0xFF10B981) to "Listening"
        OutboundCallStatus.PROCESSING.name -> Color(0xFF8B5CF6) to "Processing"
        OutboundCallStatus.ENDING.name -> Color(0xFFF59E0B) to "Ending"
        OutboundCallStatus.COMPLETED.name -> Color(0xFF10B981) to "Completed"
        OutboundCallStatus.FAILED.name -> Color(0xFFEF4444) to "Failed"
        else -> Color(0xFF6B7280) to status
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("outbound_status_header"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .scale(if (!isTerminal) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(badgeColor)
                )

                Column {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (mode == "AI") "AI Talk (Server Telephony)" else "Human Talk (Device Dialer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = mode,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FailedCallGuidanceCard(
    reason: String,
    onOpenProviderSetup: () -> Unit,
    onOpenSimulator: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("outbound_failed_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isNotConfigured = reason.contains("NOT CONFIGURED", ignoreCase = true) ||
                reason.contains("not configured", ignoreCase = true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444))
                Text(
                    text = if (isNotConfigured) "NOT CONFIGURED — Provider Required" else "Outbound AI Call Not Connected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            }
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenProviderSetup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SettingsPhone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Provider Setup", fontSize = 12.sp)
                }
                Button(
                    onClick = onOpenSimulator,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Demo Mode", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(event: TranscriptEvent, recipientLabel: String = "Recipient") {
    val isAi = event.speaker == "dinthi_ai"
    val isSystem = event.speaker == "system"
    val isUser = event.speaker == "user"

    if (isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = event.text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    } else {
        val alignment = if (isAi || isUser) Alignment.End else Alignment.Start
        val bubbleColor = when {
            isAi -> DinthiLime.copy(alpha = 0.15f)
            isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        }
        val borderColor = when {
            isAi -> DinthiLime.copy(alpha = 0.4f)
            isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else -> Color.Transparent
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment
        ) {
            Text(
                text = when {
                    isAi -> "Dinthi AI:"
                    isUser -> "You:"
                    else -> "$recipientLabel:"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isAi) DinthiLime else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PostCallSummaryCard(summary: OutboundCallSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_call_summary_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DinthiLime, modifier = Modifier.size(20.dp))
                Text(
                    text = "Post-Call Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Purpose: ${summary.reasonForCall} • Duration: ${summary.durationSeconds}s • Outcome: ${summary.outcome}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = summary.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (summary.importantDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Important Details:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                summary.importantDetails.forEach { detail ->
                    Text("• $detail", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (summary.decisions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Decisions:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                summary.decisions.forEach { dec ->
                    Text("✓ $dec", style = MaterialTheme.typography.bodySmall, color = DinthiLime)
                }
            }

            if (summary.actionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Action Items:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                summary.actionItems.forEach { act ->
                    Text("• $act", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DinthiLime.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This call record and transcript have been saved to your Dinthi Call History.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
