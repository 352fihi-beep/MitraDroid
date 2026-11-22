package com.mitra.android.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitra.android.AIConversationManager
import com.mitra.android.BatteryMonitor
import com.mitra.android.CallStatsSummary
import com.mitra.android.QualityOfLifeCampaign
import com.mitra.android.TelecomHelper
import com.mitra.android.ai.LocalInferenceResult
import com.mitra.android.ai.LocalModelEngine
import com.mitra.android.ai.LocalModelStatus
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.local.UserSettings
import com.mitra.android.data.repository.MitraRepository
import com.mitra.android.database.CallRecord
import com.mitra.android.database.ContactMemory
import com.mitra.android.database.SmsMessage
import com.mitra.android.telephony.CallSessionState
import com.mitra.android.telephony.SmsDispatcher
import com.mitra.android.telephony.VoiceSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val batteryState: BatteryMonitor.BatteryState = BatteryMonitor.BatteryState(false, 100f, 25f, "NOMINAL"),
    val missingPermissions: List<String> = emptyList(),
    val stats: CallStatsSummary = CallStatsSummary(0, 0, 0, 0L, 100f, 25f),
    val settings: UserSettings = UserSettings(),
    val modelStatus: LocalModelStatus = LocalModelStatus.RuleFallbackReady
)

@HiltViewModel
class MitraViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: MitraRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val batteryMonitor: BatteryMonitor,
    private val localModelEngine: LocalModelEngine,
    private val aiConversationManager: AIConversationManager,
    private val voiceSessionController: VoiceSessionController,
    private val smsDispatcher: SmsDispatcher,
    private val qolCampaign: QualityOfLifeCampaign
) : ViewModel() {

    val settingsFlow: StateFlow<UserSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val callSessionState: StateFlow<CallSessionState> = voiceSessionController.sessionState

    val allMessagesFlow: StateFlow<List<SmsMessage>> = repository.allMessagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allCallsFlow: StateFlow<List<CallRecord>> = repository.allCallsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allMemoriesFlow: StateFlow<List<ContactMemory>> = repository.allMemoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val localModelStatusFlow: StateFlow<LocalModelStatus> = localModelEngine.statusFlow

    private val _batteryState = MutableStateFlow(batteryMonitor.getBatteryState())
    private val _missingPermissions = MutableStateFlow(qolCampaign.missingRuntimePermissions())

    val homeUiState: StateFlow<HomeUiState> = combine(
        _batteryState,
        _missingPermissions,
        allCallsFlow,
        allMessagesFlow,
        settingsFlow,
        localModelStatusFlow
    ) { values ->
        val battery = values[0] as BatteryMonitor.BatteryState
        @Suppress("UNCHECKED_CAST")
        val missing = values[1] as List<String>
        @Suppress("UNCHECKED_CAST")
        val calls = values[2] as List<CallRecord>
        @Suppress("UNCHECKED_CAST")
        val messages = values[3] as List<SmsMessage>
        val settings = values[4] as UserSettings
        val modelStatus = values[5] as LocalModelStatus

        val avgDuration = if (calls.isEmpty()) 0L else calls.sumOf { it.durationSeconds } / calls.size
        HomeUiState(
            batteryState = battery,
            missingPermissions = missing,
            stats = CallStatsSummary(
                totalCalls = calls.size,
                totalSms = messages.size,
                flaggedPhishingSms = messages.count { it.isPhishingFlagged },
                averageCallSeconds = avgDuration,
                batteryLevel = battery.level,
                batteryTempCelsius = battery.temperatureCelsius
            ),
            settings = settings,
            modelStatus = modelStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _smsDraft = MutableStateFlow("")
    val smsDraft: StateFlow<String> = _smsDraft.asStateFlow()

    private val _isGeneratingDraft = MutableStateFlow(false)
    val isGeneratingDraft: StateFlow<Boolean> = _isGeneratingDraft.asStateFlow()

    private val _keyTestFeedback = MutableStateFlow<Map<String, String>>(emptyMap())
    val keyTestFeedback: StateFlow<Map<String, String>> = _keyTestFeedback.asStateFlow()

    private val _localInferenceTestResult = MutableStateFlow<LocalInferenceResult?>(null)
    val localInferenceTestResult: StateFlow<LocalInferenceResult?> = _localInferenceTestResult.asStateFlow()

    init {
        viewModelScope.launch {
            qolCampaign.setupInitialConfig()
        }
    }

    fun refreshTelemetry() {
        _batteryState.value = batteryMonitor.getBatteryState()
        _missingPermissions.value = qolCampaign.missingRuntimePermissions()
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    // --- Call / Telecom Actions ---

    fun startOrPlaceCall(number: String) {
        val target = number.trim().ifBlank { "+1-787-555-0142" }
        val placedViaTelecom = TelecomHelper.placeSelfManagedCall(appContext, target)
        if (!placedViaTelecom || !voiceSessionController.sessionState.value.isActive) {
            voiceSessionController.startCallSession(
                callId = "call-${System.currentTimeMillis()}",
                contactNumber = target,
                direction = "OUTGOING"
            )
        }
    }

    fun sendCallUtterance(utterance: String) {
        voiceSessionController.processIncomingUtterance(utterance)
    }

    fun sendDtmf(tone: Char) {
        voiceSessionController.sendDtmfTone(tone)
    }

    fun toggleMute() = voiceSessionController.toggleMute()
    fun toggleSpeaker() = voiceSessionController.toggleSpeaker()
    fun startSpeechListening() = voiceSessionController.startSpeechListening()
    fun stopSpeechListening() = voiceSessionController.stopSpeechListening()
    fun endActiveCall() = voiceSessionController.endCallSession()

    // --- SMS Actions ---

    fun updateSmsDraft(text: String) {
        _smsDraft.value = text
    }

    fun sendSmsMessage(recipient: String, message: String) {
        viewModelScope.launch {
            val result = smsDispatcher.sendSms(recipient, message, isFromAi = false)
            result.onSuccess { status ->
                _smsDraft.value = ""
                _statusBanner.value = status
            }.onFailure { err ->
                _statusBanner.value = "SMS failed: ${err.message}"
            }
        }
    }

    fun generateAiSmsReply(contactId: String, latestIncomingText: String) {
        viewModelScope.launch {
            _isGeneratingDraft.value = true
            val prompt = latestIncomingText.ifBlank {
                "Draft a warm, concise check-in reply for $contactId."
            }
            val result = aiConversationManager.generateReply(
                prompt = prompt,
                contact = contactId,
                isCall = false
            )
            _smsDraft.value = result.text
            _isGeneratingDraft.value = false
            _statusBanner.value = "Draft ready (${result.providerUsed}, ${result.latencyMs}ms)"
        }
    }

    fun simulateIncomingSms(sender: String, body: String) {
        viewModelScope.launch {
            val flagged = qolCampaign.detectPhishing(body)
            repository.recordSms(
                contactId = sender.ifBlank { "+1-787-555-0199" },
                message = body,
                isFromAi = false,
                isPhishingFlagged = flagged,
                deliveryStatus = "RECEIVED"
            )
            if (flagged) {
                _statusBanner.value = "Phishing Guard flagged suspicious SMS from $sender"
            }
        }
    }

    // --- History & QoL Actions ---

    fun exportHistoryCsv() {
        viewModelScope.launch {
            runCatching { qolCampaign.exportToCsv() }
                .onSuccess { file -> _statusBanner.value = "Exported CSV: ${file.name}" }
                .onFailure { err -> _statusBanner.value = "CSV export failed: ${err.message}" }
        }
    }

    fun showDailyReport() {
        viewModelScope.launch {
            _statusBanner.value = qolCampaign.dailyReport()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _statusBanner.value = "Cleared call logs, SMS threads, and contact memories."
        }
    }

    // --- Settings & API Keys ---

    fun saveApiKey(provider: String, key: String) {
        viewModelScope.launch {
            preferencesRepository.saveProviderApiKey(provider, key)
            _statusBanner.value = "Saved $provider API key locally."
        }
    }

    fun testProviderKey(provider: String, key: String) {
        viewModelScope.launch {
            _keyTestFeedback.update { it + (provider to "Testing...") }
            val result = aiConversationManager.verifyProviderKey(provider, key)
            result.onSuccess { msg ->
                _keyTestFeedback.update { it + (provider to "OK: ${msg.take(24)}") }
            }.onFailure { err ->
                _keyTestFeedback.update { it + (provider to "Error: ${err.message?.take(40)}") }
            }
        }
    }

    fun setPrimaryProvider(provider: String) {
        viewModelScope.launch { preferencesRepository.setPrimaryProvider(provider) }
    }

    fun updatePowerThresholds(lowBattery: Int, thermal: Float) {
        viewModelScope.launch { qolCampaign.optimizeBattery(lowBattery, thermal) }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkThemeEnabled(enabled) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColorEnabled(enabled) }
    }

    fun setAccentTheme(theme: String) {
        viewModelScope.launch { preferencesRepository.setAccentTheme(theme) }
    }

    fun toggleAutoReplySms(enabled: Boolean) {
        viewModelScope.launch { qolCampaign.autoReplySms(enabled) }
    }

    fun togglePhishingGuard(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPhishingGuardEnabled(enabled) }
    }

    fun toggleReadAloudSms(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReadAloudSmsEnabled(enabled) }
    }

    fun toggleBlockUnknown(enabled: Boolean) {
        viewModelScope.launch { qolCampaign.blockUnknownNumbers(enabled) }
    }

    fun toggleAutoMuteSpam(enabled: Boolean) {
        viewModelScope.launch { qolCampaign.autoMuteSpam(enabled) }
    }

    fun toggleVibrateOnRing(enabled: Boolean) {
        viewModelScope.launch { qolCampaign.vibrateOnRinging(enabled) }
    }

    fun toggleDnd(enabled: Boolean) {
        viewModelScope.launch { qolCampaign.setDoNotDisturb(enabled) }
    }

    fun toggleSpeechToText(enabled: Boolean) {
        viewModelScope.launch { qolCampaign.enableSpeechToText(enabled) }
    }

    fun addWhitelistContact(contact: String) {
        viewModelScope.launch { preferencesRepository.addWhitelistedContact(contact) }
    }

    fun removeWhitelistContact(contact: String) {
        viewModelScope.launch { preferencesRepository.removeWhitelistedContact(contact) }
    }

    fun clearAllApiKeys() {
        viewModelScope.launch {
            qolCampaign.rotateApiKeys()
            _keyTestFeedback.value = emptyMap()
            _statusBanner.value = "All API keys cleared from device."
        }
    }

    // --- Voice & MediaPipe Studio ---

    fun importLocalModel(uri: Uri) {
        viewModelScope.launch {
            val result = localModelEngine.importModelFromUri(uri)
            result.onSuccess { path ->
                preferencesRepository.setLocalModelPath(path)
                _statusBanner.value = "MediaPipe model loaded successfully."
            }.onFailure { err ->
                _statusBanner.value = "Model fallback active: ${err.message}"
            }
        }
    }

    fun unloadLocalModel() {
        viewModelScope.launch {
            localModelEngine.unloadModel()
            preferencesRepository.setLocalModelPath("")
            _statusBanner.value = "Unloaded local weights; rule fallback active."
        }
    }

    fun updateTtsConfig(rate: Float, pitch: Float, localeTag: String) {
        viewModelScope.launch {
            preferencesRepository.updateTtsSettings(rate, pitch, localeTag)
        }
    }

    fun previewTtsVoice(sampleText: String, rate: Float, pitch: Float, localeTag: String) {
        voiceSessionController.speakPreview(sampleText, rate, pitch, localeTag)
    }

    fun runLocalModelDiagnostic(prompt: String) {
        viewModelScope.launch {
            val res = localModelEngine.generateResponseLocally(
                prompt = prompt.ifBlank { "Oprima 2 para servicio al cliente en español" },
                contactContext = "Diagnostic probe",
                isLowPower = false
            )
            _localInferenceTestResult.value = res
        }
    }
}
