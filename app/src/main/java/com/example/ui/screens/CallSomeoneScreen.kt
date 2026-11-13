package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DinthiLime
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSomeoneScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    var contactName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var callPurpose by remember { mutableStateOf("") }
    var optionalInstructions by remember { mutableStateOf("") }
    var callHandlingMode by remember { mutableStateOf("AI") } // "AI" or "HUMAN"
    var showConfirmDialog by remember { mutableStateOf(false) }

    val outboundPlan by viewModel.outboundPlan.collectAsStateWithLifecycle()
    val isPlanning by viewModel.isPlanningOutbound.collectAsStateWithLifecycle()
    val outboundError by viewModel.outboundErrorMessage.collectAsStateWithLifecycle()
    val isOutboundConnecting by viewModel.isOutboundConnecting.collectAsStateWithLifecycle()
    val telephonyStatus by viewModel.telephonyStatus.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.preferences.userDisplayName.collectAsStateWithLifecycle()
    val effectiveUserName = userDisplayName.ifBlank { "the user" }
    val isProviderConfigured = telephonyStatus?.isConfigured == true

    LaunchedEffect(Unit) {
        viewModel.fetchTelephonyStatus()
    }

    // Real device Contact Picker (0-permission standard Android contact picker intent)
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            try {
                val cursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER),
                    null, null, null
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val hasPhoneIdx = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        val id = if (idIdx >= 0) c.getString(idIdx) else ""
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else ""
                        val hasPhone = if (hasPhoneIdx >= 0) c.getInt(hasPhoneIdx) else 0

                        if (name.isNotBlank()) contactName = name

                        if (hasPhone > 0) {
                            val pCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(id),
                                null
                            )
                            pCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIdx >= 0) {
                                        phoneNumber = pc.getString(numIdx)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not read contact info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call Someone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp)
                    .testTag("call_someone_list"),
                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(DinthiLime.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = DinthiLime,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Ask Dinthi to Help You Call",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Select a real contact or enter a phone number. Dinthi prepares speaking points and requires explicit confirmation before dialing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Provider Configuration Notice (Honest NOT CONFIGURED banner when provider isn't wired)
                if (!isProviderConfigured) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("telephony_not_configured_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF59E0B)
                                    ) {
                                        Text(
                                            text = "NOT CONFIGURED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Outbound AI Telephony Provider",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Real AI phone calls require a configured server-side telephony provider (Twilio + PUBLIC_WEBHOOK_URL). Attempting AI Talk now will record a failed session until credentials are added on the server. Human Talk places real calls through your device dialer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Choose Contact Button
                item {
                    OutlinedButton(
                        onClick = { contactPickerLauncher.launch(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pick_device_contact_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Real Contact from Device", fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            label = { Text("Contact Name") },
                            placeholder = { Text("Leave blank if unknown") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DinthiLime)
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number") },
                            placeholder = { Text("e.g. +91 98765 43210") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_phone_number_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DinthiLime)
                        )

                        OutlinedTextField(
                            value = callPurpose,
                            onValueChange = { callPurpose = it },
                            label = { Text("Intended Call Purpose / Goal") },
                            placeholder = { Text("e.g. Confirm parcel status or check doctor availability") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DinthiLime)
                        )

                        OutlinedTextField(
                            value = optionalInstructions,
                            onValueChange = { optionalInstructions = it },
                            label = { Text("Optional Instructions for Dinthi AI") },
                            placeholder = { Text("e.g. Ask for tracking ID, keep call under 2 minutes") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_optional_instructions_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DinthiLime)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "How should this call be handled?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { callHandlingMode = "AI" }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = callHandlingMode == "AI",
                                        onClick = { callHandlingMode = "AI" },
                                        colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = DinthiLime)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("Dinthi AI will talk to the recipient.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            if (!isProviderConfigured) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "NOT CONFIGURED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFF59E0B),
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text("AI introduces itself as an assistant calling on behalf of $effectiveUserName, never impersonating you, and logs live transcripts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { callHandlingMode = "HUMAN" }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = callHandlingMode == "HUMAN",
                                        onClick = { callHandlingMode = "HUMAN" },
                                        colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = DinthiLime)
                                    )
                                    Column {
                                        Text("I will talk myself", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Connects call directly through your phone dialer so you can speak directly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                if (outboundPlan != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = DinthiLime, modifier = Modifier.size(18.dp))
                                    Text("Suggested Opening Script", fontWeight = FontWeight.Bold, color = DinthiLime)
                                }
                                Text(
                                    text = outboundPlan!!.openingScript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Talking Points:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                outboundPlan!!.talkingPoints.forEach { pt ->
                                    Text("• $pt", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (phoneNumber.isNotBlank() && callPurpose.isNotBlank()) {
                                    viewModel.planOutboundCall(
                                        recipientName = contactName.ifBlank { phoneNumber },
                                        phoneNumber = phoneNumber.trim(),
                                        goal = callPurpose.trim()
                                    )
                                } else {
                                    Toast.makeText(context, "Please enter phone number and purpose", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isPlanning && phoneNumber.isNotBlank() && callPurpose.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (isPlanning) "Preparing..." else "Generate Plan")
                        }

                        Button(
                            onClick = {
                                if (phoneNumber.isNotBlank()) {
                                    viewModel.clearOutboundError()
                                    showConfirmDialog = true
                                } else {
                                    Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = phoneNumber.isNotBlank() && !isOutboundConnecting,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("dial_call_action_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOutboundConnecting) "Connecting..." else "Place Call",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                if (!outboundError.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = outboundError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Transparency Guarantee: Dinthi AI never dials calls autonomously without your explicit authorization. AI Talk routes via Dinthi Secure Backend & Telephony Provider; Human Talk opens your phone dialer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = { viewModel.navigateTo(com.example.ui.navigation.Screen.TelephonyProviderSetup) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = DinthiLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "View Telephony Provider & TRAI Status",
                            color = DinthiLime,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(if (callHandlingMode == "AI") "Confirm Outbound AI Call" else "Confirm Direct Call")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Recipient: ${contactName.ifBlank { "Unknown" }}\nNumber: $phoneNumber\nGoal: ${callPurpose.ifBlank { "General Inquiry" }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (callHandlingMode == "AI") {
                        Text(
                            text = "Dinthi AI will talk to the recipient.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = DinthiLime
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DinthiLime.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Opening AI Disclosure:\n\"Hello, I'm Dinthi AI, an AI assistant calling on behalf of $effectiveUserName. Is this a good time to talk?\"",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = "You will talk yourself. Your standard phone dialer will be launched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        val targetName = contactName.ifBlank { phoneNumber }
                        val targetNumber = phoneNumber.trim()
                        val targetGoal = callPurpose.ifBlank { "General Inquiry" }
                        val targetNotes = optionalInstructions.trim()

                        if (callHandlingMode == "AI") {
                            // Launch Real Outgoing AI Calling Session
                            viewModel.startOutboundCall(
                                recipientName = targetName,
                                phoneNumber = targetNumber,
                                goal = targetGoal,
                                notes = targetNotes,
                                mode = "AI"
                            )
                        } else {
                            // Human Call: Log outbound call and launch system dialer
                            viewModel.startOutboundCall(
                                recipientName = targetName,
                                phoneNumber = targetNumber,
                                goal = targetGoal,
                                notes = targetNotes,
                                mode = "HUMAN"
                            )
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$targetNumber")
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DinthiLime)
                ) {
                    Text(if (callHandlingMode == "AI") "Start AI Call" else "Dial Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
