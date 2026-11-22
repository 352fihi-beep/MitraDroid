package com.mitra.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mitra.android.database.ContactMemory
import com.mitra.android.database.SmsMessage

@Composable
fun SmsThreadsScreen(
    messages: List<SmsMessage>,
    memories: List<ContactMemory>,
    draft: String,
    isGeneratingDraft: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    onDraftChange: (String) -> Unit,
    onGenerateAiDraft: (contactId: String, latestText: String) -> Unit,
    onSendSms: (contactId: String, message: String) -> Unit,
    onSimulateIncomingSms: (sender: String, body: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val grouped = messages.groupBy { it.contactId }
    val contacts = grouped.keys.toList()
    var selectedContact by rememberSaveable(contacts) {
        mutableStateOf(contacts.firstOrNull() ?: "+1-787-555-0142")
    }
    var newContactInput by rememberSaveable { mutableStateOf("") }
    val isTwoPane = widthSizeClass != WindowWidthSizeClass.Compact

    val threadListPane: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(
            modifier = paneModifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newContactInput,
                    onValueChange = { newContactInput = it },
                    label = { Text("Contact / #") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sms_new_contact_input")
                )
                FilledTonalButton(
                    onClick = {
                        if (newContactInput.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedContact = newContactInput.trim()
                            newContactInput = ""
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sms_open_thread_button")
                ) {
                    Text("Open")
                }
            }

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No SMS threads yet. Open a number or simulate an incoming message.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(contacts, key = { it }) { contact ->
                        val threadMsgs = grouped[contact].orEmpty()
                        val latest = threadMsgs.firstOrNull()
                        val isSelected = contact == selectedContact
                        Card(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedContact = contact
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sms_thread_$contact")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(contact, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${threadMsgs.size} msgs",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (latest != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = latest.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val conversationPane: @Composable (Modifier) -> Unit = { paneModifier ->
        val activeMessages = grouped[selectedContact].orEmpty().sortedBy { it.timestamp }
        val contactMemory = memories.firstOrNull { it.contactId == selectedContact }
        val latestIncoming = activeMessages.lastOrNull { !it.isFromAi }?.message.orEmpty()

        Column(
            modifier = paneModifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedContact, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = contactMemory?.memorySummary ?: "Room memory ready to index this contact.",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSimulateIncomingSms(
                                selectedContact,
                                "Urgent action: verify your account at http://bit.ly/fake-check"
                            )
                        },
                        modifier = Modifier.height(48.dp).testTag("sms_test_phishing_button")
                    ) {
                        Text("Test Guard")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeMessages, key = { it.id }) { sms ->
                    val alignment = if (sms.isFromAi) Alignment.End else Alignment.Start
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = alignment
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when {
                                sms.isPhishingFlagged -> MaterialTheme.colorScheme.errorContainer
                                sms.isFromAi -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (sms.isPhishingFlagged) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Phishing warning",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Phishing Risk Flagged",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(sms.message, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${if (sms.isFromAi) "Mitra" else "Contact"} • ${sms.deliveryStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("Reply to $selectedContact") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sms_reply_input")
                )
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onGenerateAiDraft(selectedContact, latestIncoming)
                    },
                    enabled = !isGeneratingDraft,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sms_ai_draft_button")
                ) {
                    if (isGeneratingDraft) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Generate AI reply")
                    }
                }
                Button(
                    onClick = {
                        if (draft.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSendSms(selectedContact, draft)
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sms_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send SMS")
                }
            }
        }
    }

    if (isTwoPane) {
        Row(modifier = modifier.fillMaxSize()) {
            threadListPane(Modifier.weight(0.38f).fillMaxHeight())
            conversationPane(Modifier.weight(0.62f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.34f).fillMaxWidth()) {
                threadListPane(Modifier.fillMaxSize())
            }
            Box(modifier = Modifier.weight(0.66f).fillMaxWidth()) {
                conversationPane(Modifier.fillMaxSize())
            }
        }
    }
}
