package com.mitra.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mitra.android.ai.LocalInferenceResult
import com.mitra.android.ai.LocalModelStatus
import com.mitra.android.data.local.UserSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceTrainingScreen(
    settings: UserSettings,
    modelStatus: LocalModelStatus,
    diagnosticResult: LocalInferenceResult?,
    onImportModelUri: (Uri) -> Unit,
    onUnloadModel: () -> Unit,
    onUpdateTtsConfig: (Float, Float, String) -> Unit,
    onPreviewVoice: (String, Float, Float, String) -> Unit,
    onRunLocalDiagnostic: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var rate by remember(settings.ttsSpeechRate) { mutableFloatStateOf(settings.ttsSpeechRate) }
    var pitch by remember(settings.ttsPitch) { mutableFloatStateOf(settings.ttsPitch) }
    var localeTag by remember(settings.ttsLocaleTag) { mutableStateOf(settings.ttsLocaleTag) }
    var sampleSpeech by rememberSaveable {
        mutableStateOf("Saludos! Soy Mitra, listo para ayudarte con tus llamadas y mensajes.")
    }
    var diagnosticPrompt by rememberSaveable {
        mutableStateOf("Oprima 2 para servicio al cliente")
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportModelUri(uri)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("voice_training_list"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "MediaPipe Edge LLM (.task / .bin)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val statusDesc = when (modelStatus) {
                        is LocalModelStatus.MediaPipeReady ->
                            "Loaded: ${modelStatus.modelName} (${modelStatus.fileSizeMb} MB)"
                        is LocalModelStatus.Loading ->
                            "Loading quantized weights: ${modelStatus.modelName}..."
                        is LocalModelStatus.Error ->
                            "Fallback Active: ${modelStatus.message}"
                        LocalModelStatus.RuleFallbackReady ->
                            "No .task model imported yet — bilingual on-device rule fallback active."
                    }
                    Text(
                        text = statusDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("local_model_status_text")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                modelPickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_mediapipe_model_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Import local model")
                            Text(" Import Model")
                        }
                        if (modelStatus is LocalModelStatus.MediaPipeReady) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onUnloadModel()
                                },
                                modifier = Modifier.height(48.dp).testTag("unload_model_button")
                            ) {
                                Text("Unload")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Voice Synthesis Tuning (TTS)", style = MaterialTheme.typography.titleMedium)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("es-PR" to "Español (PR)", "en-US" to "English (US)", "es-ES" to "Español (ES)").forEach { (tag, label) ->
                            FilterChip(
                                selected = localeTag == tag,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    localeTag = tag
                                    onUpdateTtsConfig(rate, pitch, tag)
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("tts_locale_$tag")
                            )
                        }
                    }

                    Text("Speech Rate: ${"%.2f".format(rate)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = rate,
                        onValueChange = { rate = it },
                        onValueChangeFinished = { onUpdateTtsConfig(rate, pitch, localeTag) },
                        valueRange = 0.6f..1.8f,
                        modifier = Modifier.testTag("tts_rate_slider")
                    )

                    Text("Voice Pitch: ${"%.2f".format(pitch)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        onValueChangeFinished = { onUpdateTtsConfig(rate, pitch, localeTag) },
                        valueRange = 0.6f..1.6f,
                        modifier = Modifier.testTag("tts_pitch_slider")
                    )

                    OutlinedTextField(
                        value = sampleSpeech,
                        onValueChange = { sampleSpeech = it },
                        label = { Text("Voice preview phrase") },
                        modifier = Modifier.fillMaxWidth().testTag("tts_preview_input")
                    )

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUpdateTtsConfig(rate, pitch, localeTag)
                            onPreviewVoice(sampleSpeech, rate, pitch, localeTag)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("tts_preview_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview TTS voice")
                        Text(" Speak Preview")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Edge Inference Diagnostic", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = diagnosticPrompt,
                        onValueChange = { diagnosticPrompt = it },
                        label = { Text("Test local prompt") },
                        modifier = Modifier.fillMaxWidth().testTag("local_diag_input")
                    )
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRunLocalDiagnostic(diagnosticPrompt)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_local_diag_button")
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Run local diagnostic")
                        Text(" Run Edge Probe")
                    }
                    if (diagnosticResult != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${diagnosticResult.mode.name} • ${diagnosticResult.latencyMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = diagnosticResult.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("local_diag_output")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
