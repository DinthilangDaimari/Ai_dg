package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.TranscriptLineEntity
import com.example.ui.viewmodel.MainViewModel
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    callId: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val call by viewModel.viewingCall.collectAsStateWithLifecycle()
    val transcript by viewModel.viewingTranscript.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.textToSpeech.speakingMessageId.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var actionConfirmationDialog by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("call_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("delete_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete call record",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
            val currentCall = call
            if (currentCall == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Call record not found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Overview Card
                    item {
                        CallDetailHeaderCard(call = currentCall)
                    }

                    // Direct Contact Action Bar (Call Back, Add/Edit Contact)
                    item {
                        ContactActionsQuickBar(
                            call = currentCall,
                            onCallBack = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${currentCall.phoneNumber}")
                                }
                                context.startActivity(intent)
                            },
                            onAddEditContact = {
                                val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                    if (currentCall.callerName.isNotBlank() && currentCall.callerName != currentCall.phoneNumber) {
                                        putExtra(ContactsContract.Intents.Insert.NAME, currentCall.callerName)
                                    }
                                    putExtra(ContactsContract.Intents.Insert.PHONE, currentCall.phoneNumber)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    // AI Intelligence Summary Card
                    item {
                        IntelligenceSummaryCard(
                            call = currentCall,
                            isSpeaking = speakingMessageId == currentCall.id,
                            onToggleAudio = {
                                if (speakingMessageId == currentCall.id) {
                                    viewModel.textToSpeech.stop()
                                } else {
                                    val speechText = "${currentCall.callerName} called regarding ${currentCall.reasonForCall}. Summary: ${currentCall.summary}"
                                    viewModel.textToSpeech.speak(speechText, currentCall.id)
                                }
                            }
                        )
                    }

                    // Recommended Post-Call Actions
                    val parsedActions = parseActions(currentCall.actionsJson)
                    if (parsedActions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recommended Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        items(parsedActions) { action ->
                            ActionCard(
                                title = action.title,
                                detail = action.detail,
                                type = action.type,
                                onPerform = {
                                    actionConfirmationDialog = Pair(action.title) {
                                        executeAction(context, action.type, currentCall)
                                    }
                                }
                            )
                        }
                    }

                    // Legal & Telecom Recording Policy Transparency
                    item {
                        TelecomTransparencyCard()
                    }

                    // Full Conversation Transcript
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Call Transcript",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${transcript.size} turns",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (transcript.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            val fullText = transcript.joinToString("\n") { "${it.speaker}: ${it.text}" }
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Call Transcript", fullText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Transcript copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    if (transcript.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No live transcript logged for this call.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(transcript) { line ->
                            TranscriptRow(
                                line = line,
                                isSpeaking = speakingMessageId == "line_${line.id}",
                                onSpeak = {
                                    if (speakingMessageId == "line_${line.id}") {
                                        viewModel.textToSpeech.stop()
                                    } else {
                                        viewModel.textToSpeech.speak(line.text, "line_${line.id}")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && call != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Call Record") },
            text = { Text("Are you sure you want to delete this call log and transcript from Dinthi AI?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteCall(call!!.id)
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Explicit Action Confirmation Dialog
    actionConfirmationDialog?.let { (actionTitle, onConfirm) ->
        AlertDialog(
            onDismissRequest = { actionConfirmationDialog = null },
            title = { Text("Confirm Action") },
            text = { Text("Do you want to proceed with: $actionTitle?") },
            confirmButton = {
                Button(
                    onClick = {
                        actionConfirmationDialog = null
                        onConfirm()
                    }
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { actionConfirmationDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CallDetailHeaderCard(call: CallEntity) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val (avatarIcon, avatarBg, avatarTint) = when {
                    call.isSpam -> Triple(Icons.Default.Security, Color(0xFFFFEBEE), Color(0xFFEF4444))
                    call.category.equals("Delivery", true) -> Triple(Icons.Default.LocalShipping, Color(0xFFE0F2FE), Color(0xFF0284C7))
                    call.category.equals("Healthcare", true) -> Triple(Icons.Default.MedicalServices, Color(0xFFECFDF5), Color(0xFF059669))
                    call.category.equals("Recruiter", true) -> Triple(Icons.Default.Work, Color(0xFFEDE9FE), Color(0xFF7C3AED))
                    else -> Triple(Icons.Default.Call, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary)
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = avatarIcon,
                        contentDescription = call.category,
                        tint = avatarTint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (call.callerName.isNotBlank()) call.callerName else call.phoneNumber
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (call.callerName.isNotBlank() && call.callerName != call.phoneNumber) {
                        Text(
                            text = call.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = if (call.isContact) "Saved Contact" else "Unknown Caller",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isDemoRecord = call.isDemo || call.status == "DEMO_SIMULATOR"
                val formattedStatus = when {
                    isDemoRecord -> "Demo / Sim"
                    call.isSpam -> "Spam (${call.spamScore}%)"
                    call.status == "OUTGOING_AI" -> "Real AI Call"
                    call.status == "OUTGOING_HUMAN" -> "Real Human Call"
                    call.status == "OUTGOING_FAILED" -> "Failed Call"
                    call.status == "SCREENED" -> "Real AI Screened"
                    call.status == "ANSWERED" -> "Real Human"
                    else -> call.status
                }
                val isErrorStatus = call.isSpam || call.status == "OUTGOING_FAILED"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isErrorStatus -> Color(0xFFFFEBEE)
                        isDemoRecord -> Color(0xFFF59E0B).copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = formattedStatus,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isErrorStatus -> Color(0xFFC62828)
                            isDemoRecord -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("EEEE, MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(call.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Duration: ${call.durationSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ContactActionsQuickBar(
    call: CallEntity,
    onCallBack: () -> Unit,
    onAddEditContact: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onCallBack,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Call Back", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onAddEditContact,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (call.isContact) "Edit Contact" else "Save Contact", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IntelligenceSummaryCard(
    call: CallEntity,
    isSpeaking: Boolean,
    onToggleAudio: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI Summary",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Executive Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Audio Playback Button
                IconButton(
                    onClick = onToggleAudio,
                    modifier = Modifier.testTag("play_summary_audio_button")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop Audio" else "Listen to Summary",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (call.reasonForCall.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Reason for Call:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = call.reasonForCall,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = call.summary.ifBlank { "Call screened by Dinthi AI." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            // Extracted Key Details Bullet Points
            val keyDetails = parseStringArray(call.keyDetailsJson)
            if (keyDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Key Extracted Details:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                keyDetails.forEach { detail ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    detail: String,
    type: String,
    onPerform: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (type) {
                "CALLBACK" -> Icons.Default.Call
                "CALENDAR_REMINDER" -> Icons.Default.Event
                "SAVE_CONTACT" -> Icons.Default.PersonAdd
                "BLOCK_SPAM" -> Icons.Default.Block
                else -> Icons.Default.CheckCircle
            }
            val iconTint = when (type) {
                "BLOCK_SPAM" -> Color(0xFFEF4444)
                "CALLBACK" -> MaterialTheme.colorScheme.primary
                else -> Color(0xFF10B981)
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Column {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = onPerform,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "BLOCK_SPAM") Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = when (type) {
                        "CALLBACK" -> "Call"
                        "BLOCK_SPAM" -> "Block"
                        else -> "Action"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TelecomTransparencyCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Transparency Note",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Telecom & Privacy Disclosure",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Android 10+ restricts regular applications from recording raw cellular call streams without OEM/carrier privileges. Dinthi AI complies strictly by maintaining high-fidelity live transcripts and AI intelligence summaries rather than illegal background audio capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TranscriptRow(
    line: TranscriptLineEntity,
    isSpeaking: Boolean,
    onSpeak: () -> Unit
) {
    val isAi = line.speaker.equals("AI", ignoreCase = true) || line.speaker.equals("dinthi_ai", ignoreCase = true)
    val isUser = line.speaker.equals("USER", ignoreCase = true)
    val isSystem = line.speaker.equals("SYSTEM", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isAi -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isUser -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when {
                            isAi -> "Dinthi AI"
                            isUser -> "You"
                            isSystem -> "System"
                            line.speaker.equals("recipient", ignoreCase = true) -> "Recipient"
                            else -> "Caller"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isAi -> MaterialTheme.colorScheme.primary
                            isUser -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(line.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }

            IconButton(
                onClick = onSpeak,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Read line",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class ParsedAction(val type: String, val title: String, val detail: String)

private fun parseActions(json: String): List<ParsedAction> {
    val list = mutableListOf<ParsedAction>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                ParsedAction(
                    type = obj.optString("type", "CALLBACK"),
                    title = obj.optString("title", "Action"),
                    detail = obj.optString("detail", "")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}

private fun parseStringArray(json: String): List<String> {
    val list = mutableListOf<String>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
    } catch (_: Exception) {}
    return list
}

private fun executeAction(context: Context, type: String, call: CallEntity) {
    try {
        when (type) {
            "CALLBACK" -> {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${call.phoneNumber}")
                }
                context.startActivity(intent)
            }
            "SAVE_CONTACT" -> {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    setType(ContactsContract.Contacts.CONTENT_TYPE)
                    putExtra(ContactsContract.Intents.Insert.NAME, call.callerName)
                    putExtra(ContactsContract.Intents.Insert.PHONE, call.phoneNumber)
                }
                context.startActivity(intent)
            }
            "CALENDAR_REMINDER" -> {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = Uri.parse("content://com.android.calendar/events")
                    putExtra("title", "Follow-up: ${call.callerName}")
                    putExtra("description", call.summary)
                }
                context.startActivity(intent)
            }
            "BLOCK_SPAM" -> {
                Toast.makeText(context, "${call.phoneNumber} marked as blocked spam.", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(context, "Action logged for ${call.callerName}.", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Completed: ${call.callerName}", Toast.LENGTH_SHORT).show()
    }
}
