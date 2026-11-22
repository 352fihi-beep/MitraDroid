package com.mitra.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mitra.android.telephony.CallSessionState

@Composable
fun InCallScreen(
    sessionState: CallSessionState,
    onStartCall: (String) -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleListening: () -> Unit,
    onSendDtmf: (Char) -> Unit,
    onSendUtterance: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var dialInput by rememberSaveable { mutableStateOf(sessionState.contactNumber) }
    var utteranceInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Call Header Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = Spring.StiffnessLow))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (sessionState.isActive) sessionState.contactNumber else "Telecom Voice Console",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${sessionState.statusLabel} • ${sessionState.activeProvider} (${sessionState.activeInferenceMode})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (sessionState.dtmfSent.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "DTMF: ${sessionState.dtmfSent}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("dtmf_history_badge")
                            )
                        }
                    }
                }

                if (!sessionState.isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dialInput,
                            onValueChange = { dialInput = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dial_number_input")
                        )
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartCall(dialInput)
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("start_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Start call")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dial")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleMute()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("call_mute_button")
                        ) {
                            Icon(
                                if (sessionState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Toggle mute"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleSpeaker()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("call_speaker_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Toggle speaker")
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleListening()
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("call_stt_button")
                        ) {
                            Text(if (sessionState.isListeningStt) "Listening..." else "Listen STT")
                        }
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEndCall()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("end_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End call")
                        }
                    }
                }
            }
        }

        // Live Transcript Feed
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (sessionState.transcriptEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dial or send an IVR prompt below (e.g., \"Oprima 2 para español\") to test bilingual AI + automatic DTMF traversal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessionState.transcriptEntries, key = { it.id }) { entry ->
                        val isMitra = entry.speaker == "Mitra"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMitra) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.speaker,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (entry.detectedDtmf != null) {
                                        Text(
                                            text = "Auto DTMF: ${entry.detectedDtmf}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = entry.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Compact DTMF Pad + Utterance Input
        DtmfKeypadStrip(
            onTone = { tone ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSendDtmf(tone)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = utteranceInput,
                onValueChange = { utteranceInput = it },
                label = { Text("Caller audio / IVR prompt") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("call_utterance_input")
            )
            IconButton(
                onClick = {
                    if (utteranceInput.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendUtterance(utteranceInput)
                        utteranceInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("send_utterance_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Process utterance",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun DtmfKeypadStrip(onTone: (Char) -> Unit) {
    val tones = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tones.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { tone ->
                    FilledTonalButton(
                        onClick = { onTone(tone) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dtmf_key_$tone")
                    ) {
                        Text(tone.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
