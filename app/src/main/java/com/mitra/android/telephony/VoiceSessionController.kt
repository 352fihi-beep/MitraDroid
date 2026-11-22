package com.mitra.android.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.Connection
import android.util.Log
import androidx.core.content.ContextCompat
import com.mitra.android.AIConversationManager
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.repository.MitraRepository
import com.mitra.android.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class TranscriptEntry(
    val id: Long = System.nanoTime(),
    val speaker: String, // "Caller", "Mitra", "System"
    val text: String,
    val detectedDtmf: Char? = null,
    val inferenceMode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CallSessionState(
    val isActive: Boolean = false,
    val callId: String = "",
    val contactNumber: String = "+1-787-555-0100",
    val direction: String = "OUTGOING",
    val statusLabel: String = "IDLE",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isListeningStt: Boolean = false,
    val isSpeakingTts: Boolean = false,
    val isProcessingAi: Boolean = false,
    val dtmfSent: String = "",
    val activeProvider: String = "Standby",
    val activeInferenceMode: String = "CLOUD",
    val startedAtMs: Long = 0L,
    val transcriptEntries: List<TranscriptEntry> = emptyList()
)

@Singleton
class VoiceSessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiConversationManager: AIConversationManager,
    private val repository: MitraRepository,
    private val preferencesRepository: UserPreferencesRepository,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _sessionState = MutableStateFlow(CallSessionState())
    val sessionState: StateFlow<CallSessionState> = _sessionState.asStateFlow()

    @Volatile
    private var activeConnection: Connection? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    fun attachConnection(connection: Connection) {
        activeConnection = connection
    }

    fun startCallSession(
        callId: String,
        contactNumber: String,
        direction: String = "OUTGOING"
    ) {
        val cleanContact = contactNumber.ifBlank { "+1-787-555-0100" }
        _sessionState.value = CallSessionState(
            isActive = true,
            callId = callId,
            contactNumber = cleanContact,
            direction = direction,
            statusLabel = "ACTIVE",
            startedAtMs = System.currentTimeMillis(),
            transcriptEntries = listOf(
                TranscriptEntry(
                    speaker = "System",
                    text = "Telecom $direction session connected for $cleanContact."
                )
            )
        )
        ensureTtsInitialized()
    }

    fun onConnectionStateUpdated(statusLabel: String) {
        _sessionState.update { it.copy(statusLabel = statusLabel) }
    }

    fun processIncomingUtterance(utterance: String, connection: Connection? = activeConnection) {
        val clean = utterance.trim()
        if (clean.isEmpty()) return

        if (!_sessionState.value.isActive) {
            startCallSession(
                callId = "session-${System.currentTimeMillis()}",
                contactNumber = _sessionState.value.contactNumber,
                direction = "OUTGOING"
            )
        }

        val detectedTone = aiConversationManager.detectAndSendIVR(clean, connection)
        _sessionState.update { state ->
            val updatedDtmf = if (detectedTone != null) state.dtmfSent + detectedTone else state.dtmfSent
            state.copy(
                isProcessingAi = true,
                dtmfSent = updatedDtmf,
                transcriptEntries = state.transcriptEntries + TranscriptEntry(
                    speaker = "Caller",
                    text = clean,
                    detectedDtmf = detectedTone
                )
            )
        }

        appScope.launch {
            val contact = _sessionState.value.contactNumber
            val result = aiConversationManager.generateReply(
                prompt = clean,
                contact = contact,
                isCall = true
            )
            _sessionState.update { state ->
                state.copy(
                    isProcessingAi = false,
                    activeProvider = result.providerUsed,
                    activeInferenceMode = result.mode.name,
                    transcriptEntries = state.transcriptEntries + TranscriptEntry(
                        speaker = "Mitra",
                        text = result.text,
                        inferenceMode = result.mode.name
                    )
                )
            }
            speakText(result.text)
        }
    }

    fun sendDtmfTone(tone: Char) {
        val conn = activeConnection
        if (conn != null) {
            aiConversationManager.sendDTMF(conn, tone)
        }
        _sessionState.update { state ->
            state.copy(
                dtmfSent = state.dtmfSent + tone,
                transcriptEntries = state.transcriptEntries + TranscriptEntry(
                    speaker = "System",
                    text = "Sent DTMF tone '$tone'",
                    detectedDtmf = tone
                )
            )
        }
    }

    fun toggleMute() {
        val nextMuted = !_sessionState.value.isMuted
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isMicrophoneMute = nextMuted
        }
        _sessionState.update { it.copy(isMuted = nextMuted) }
    }

    fun toggleSpeaker() {
        val nextSpeaker = !_sessionState.value.isSpeakerOn
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = nextSpeaker
        }
        _sessionState.update { it.copy(isSpeakerOn = nextSpeaker) }
    }

    fun startSpeechListening() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            _sessionState.update {
                it.copy(
                    transcriptEntries = it.transcriptEntries + TranscriptEntry(
                        speaker = "System",
                        text = "Microphone permission required for live Speech-to-Text."
                    )
                )
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _sessionState.update {
                it.copy(
                    transcriptEntries = it.transcriptEntries + TranscriptEntry(
                        speaker = "System",
                        text = "On-device SpeechRecognizer unavailable; use quick utterance simulator below."
                    )
                )
            }
            return
        }

        appScope.launch(Dispatchers.Main) {
            runCatching {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _sessionState.update { it.copy(isListeningStt = true) }
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _sessionState.update { it.copy(isListeningStt = false) }
                    }

                    override fun onError(error: Int) {
                        _sessionState.update { it.copy(isListeningStt = false) }
                    }

                    override fun onResults(results: Bundle?) {
                        _sessionState.update { it.copy(isListeningStt = false) }
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!spoken.isNullOrBlank()) {
                            processIncomingUtterance(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PR")
                }
                recognizer.startListening(intent)
                _sessionState.update { it.copy(isListeningStt = true) }
            }.onFailure {
                _sessionState.update { it.copy(isListeningStt = false) }
            }
        }
    }

    fun stopSpeechListening() {
        appScope.launch(Dispatchers.Main) {
            runCatching { speechRecognizer?.stopListening() }
            _sessionState.update { it.copy(isListeningStt = false) }
        }
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        appScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            ensureTtsInitialized()
            if (isTtsReady) {
                applyTtsConfig(settings.ttsSpeechRate, settings.ttsPitch, settings.ttsLocaleTag)
                _sessionState.update { it.copy(isSpeakingTts = true) }
                runCatching {
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mitra-${System.currentTimeMillis()}")
                }
                _sessionState.update { it.copy(isSpeakingTts = false) }
            }
        }
    }

    fun speakPreview(text: String, rate: Float, pitch: Float, localeTag: String) {
        ensureTtsInitialized()
        if (isTtsReady) {
            applyTtsConfig(rate, pitch, localeTag)
            runCatching {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mitra-preview")
            }
        }
    }

    private fun applyTtsConfig(rate: Float, pitch: Float, localeTag: String) {
        runCatching {
            val locale = Locale.forLanguageTag(localeTag)
            textToSpeech?.language = locale
            textToSpeech?.setSpeechRate(rate)
            textToSpeech?.setPitch(pitch)
        }
    }

    private fun ensureTtsInitialized() {
        if (textToSpeech != null) return
        runCatching {
            textToSpeech = TextToSpeech(context) { status ->
                isTtsReady = (status == TextToSpeech.SUCCESS)
            }
        }.onFailure {
            Log.w("VoiceSessionController", "TTS unavailable in current environment", it)
        }
    }

    fun endCallSession() {
        val current = _sessionState.value
        runCatching { activeConnection?.onDisconnect() }
        activeConnection = null
        stopSpeechListening()

        if (current.isActive) {
            val durationSec = ((System.currentTimeMillis() - current.startedAtMs) / 1000L)
                .coerceAtLeast(1L)
            val fullTranscript = current.transcriptEntries.joinToString("\n") {
                "${it.speaker}: ${it.text}"
            }
            appScope.launch {
                repository.recordCall(
                    contactId = current.contactNumber,
                    direction = current.direction,
                    durationSeconds = durationSec,
                    transcript = fullTranscript,
                    dtmfHistory = current.dtmfSent,
                    inferenceMode = current.activeInferenceMode
                )
            }
        }

        _sessionState.update {
            it.copy(
                isActive = false,
                statusLabel = "DISCONNECTED",
                isListeningStt = false,
                isSpeakingTts = false
            )
        }
    }
}
