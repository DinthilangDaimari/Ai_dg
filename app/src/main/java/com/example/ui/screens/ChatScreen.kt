package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.ui.components.MarkdownText
import com.example.ui.components.OfflineBanner
import com.example.ui.components.VoiceVisualizerBar
import com.example.ui.theme.DinthiCyan
import com.example.ui.theme.DinthiViolet
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    viewModel: MainViewModel,
    onRequestMicrophonePermission: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConv = conversations.firstOrNull { it.id == conversationId }

    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val attachedImageUri by viewModel.attachedImageUri.collectAsStateWithLifecycle()

    val isListening by viewModel.speechRecognizer.isListening.collectAsStateWithLifecycle()
    val audioRms by viewModel.speechRecognizer.audioRms.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.speechRecognizer.liveTranscript.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.textToSpeech.speakingMessageId.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var optionsMenuExpanded by remember { mutableStateOf(false) }

    // Android Back button handler
    BackHandler {
        viewModel.navigateBack()
    }

    // Auto-scroll to bottom on new messages or streaming
    LaunchedEffect(messages.size, streamingText.length) {
        val totalItems = messages.size + (if (isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setAttachedImage(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column(
                        modifier = Modifier
                            .clickable {
                                renameInput = currentConv?.title.orEmpty()
                                showRenameDialog = true
                            }
                            .testTag("chat_title_clickable")
                    ) {
                        Text(
                            text = currentConv?.title?.ifBlank { "Dinthi AI Chat" } ?: "Dinthi AI Chat",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DinthiCyan)
                            )
                            Text(
                                text = "Dinthi AI • Tap to rename",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { optionsMenuExpanded = true },
                            modifier = Modifier.testTag("chat_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = optionsMenuExpanded,
                            onDismissRequest = { optionsMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Chat") },
                                onClick = {
                                    optionsMenuExpanded = false
                                    renameInput = currentConv?.title.orEmpty()
                                    showRenameDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    optionsMenuExpanded = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Voice visualizer if active
                VoiceVisualizerBar(
                    isListening = isListening,
                    audioRms = audioRms,
                    liveTranscript = liveTranscript,
                    onStopListening = { viewModel.stopVoiceInput() }
                )

                // Fixed Chat Composer
                ChatComposerBar(
                    inputText = inputText,
                    attachedImageUri = attachedImageUri,
                    isGenerating = isGenerating,
                    isListening = isListening,
                    onTextChange = { viewModel.setInputText(it) },
                    onAttachClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveAttachment = { viewModel.setAttachedImage(null) },
                    onMicClick = {
                        onRequestMicrophonePermission {
                            viewModel.toggleVoiceInput()
                        }
                    },
                    onSendClick = {
                        if (inputText.isNotBlank() || attachedImageUri != null) {
                            viewModel.sendMessage(
                                conversationId = conversationId,
                                prompt = inputText,
                                imageUri = attachedImageUri
                            )
                        }
                    },
                    onStopClick = { viewModel.stopGeneration() }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OfflineBanner(isCloudConnected = viewModel.aiRepository.isCloudConnected)
            }

            // Message Items
            items(messages, key = { it.id }) { message ->
                val isLastAiMessage = message.role == "assistant" && message.id == messages.lastOrNull { it.role == "assistant" }?.id
                MessageBubble(
                    message = message,
                    isSpeaking = speakingMessageId == message.id,
                    isLastAiMessage = isLastAiMessage,
                    onSpeak = { viewModel.speakMessage(message) },
                    onStopSpeak = { viewModel.stopSpeaking() },
                    onRegenerate = { viewModel.regenerateLastResponse(conversationId) }
                )
            }

            // Active streaming message chunk
            if (isGenerating) {
                item {
                    StreamingAiBubble(
                        streamingContent = streamingText,
                        onStopGeneration = { viewModel.stopGeneration() }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_rename_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameConversation(conversationId, renameInput)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("dialog_confirm_rename")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this conversation?") },
            text = { Text("This will permanently remove all messages in this conversation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteConversation(conversationId)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("dialog_confirm_delete")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    isSpeaking: Boolean,
    isLastAiMessage: Boolean,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val isUser = message.role == "user"

    val timeString = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_row" else "ai_message_row"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // USER MESSAGE BUBBLE
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(14.dp)
            ) {
                // Attached Image if present
                if (!message.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Attached photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimary)
                )

                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        } else {
            // AI ASSISTANT MESSAGE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // AI Avatar Badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(DinthiCyan, DinthiViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Dinthi AI",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        ),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            MarkdownText(
                                markdown = message.content,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 6.dp)
                            )
                        }
                    }

                    // Action Toolbar for AI message (Speak, Copy, Regenerate, Share)
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("ai_message_actions"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Read Aloud / Stop Speaking
                        IconButton(
                            onClick = { if (isSpeaking) onStopSpeak() else onSpeak() },
                            modifier = Modifier.size(32.dp).testTag("action_speak_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop Speaking" else "Read Aloud",
                                tint = if (isSpeaking) DinthiCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Copy full text
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                isCopied = true
                                Toast.makeText(context, "Response copied to clipboard", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    delay(2000L)
                                    isCopied = false
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("action_copy_button")
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                tint = if (isCopied) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Share via Intent
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Dinthi AI Response")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.size(32.dp).testTag("action_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Regenerate (available on latest AI response)
                        if (isLastAiMessage) {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier.size(32.dp).testTag("action_regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate Response",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
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
private fun StreamingAiBubble(
    streamingContent: String,
    onStopGeneration: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("streaming_ai_bubble"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(DinthiCyan, DinthiViolet))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Dinthi AI",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (streamingContent.isNotBlank()) {
                        MarkdownText(
                            markdown = streamingContent,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Dinthi AI is thinking...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Stop Generation Button
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onStopGeneration,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("stop_generation_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop generating", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ChatComposerBar(
    inputText: String,
    attachedImageUri: String?,
    isGenerating: Boolean,
    isListening: Boolean,
    onTextChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Attached Image Preview
            AnimatedVisibility(visible = attachedImageUri != null) {
                attachedImageUri?.let { uriString ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uriString,
                            contentDescription = "Attached photo preview",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Photo attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onRemoveAttachment, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Photo Attach Button
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("chat_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Voice Mic Button
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) DinthiCyan.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("chat_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictate with voice",
                        tint = if (isListening) DinthiCyan else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            "Message Dinthi AI...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // Send / Stop button
                if (isGenerating) {
                    FilledIconButton(
                        onClick = onStopClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_stop_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSendClick,
                        enabled = inputText.isNotBlank() || attachedImageUri != null,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
