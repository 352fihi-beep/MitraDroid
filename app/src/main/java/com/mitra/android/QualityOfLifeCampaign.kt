package com.mitra.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mitra.android.ai.LocalModelEngine
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.repository.MitraRepository
import com.mitra.android.telephony.PhishingAndSpamAnalyzer
import com.mitra.android.telephony.SmsDispatcher
import com.mitra.android.telephony.VoiceSessionController
import com.mitra.android.worker.DailyReportWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CallStatsSummary(
    val totalCalls: Int,
    val totalSms: Int,
    val flaggedPhishingSms: Int,
    val averageCallSeconds: Long,
    val batteryLevel: Float,
    val batteryTempCelsius: Float
)

@Singleton
class QualityOfLifeCampaign @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val repository: MitraRepository,
    private val batteryMonitor: BatteryMonitor,
    private val localModelEngine: LocalModelEngine,
    private val phishingAnalyzer: PhishingAndSpamAnalyzer,
    private val aiConversationManager: AIConversationManager,
    private val smsDispatcher: SmsDispatcher,
    private val voiceSessionController: VoiceSessionController
) {
    suspend fun setupInitialConfig() {
        repository.seedDemoThreadIfEmpty()
        TelecomHelper.ensurePhoneAccountRegistered(context)
        runCatching {
            val request = PeriodicWorkRequestBuilder<DailyReportWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "mitra_daily_qol",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    suspend fun enableDarkTheme(enabled: Boolean = true) {
        preferencesRepository.setDarkThemeEnabled(enabled)
    }

    fun autoClearCache(): Long {
        val cacheFiles = context.cacheDir.listFiles().orEmpty()
        var freedBytes = 0L
        val cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        cacheFiles.forEach { file ->
            if (file.lastModified() < cutoff && file.deleteRecursively()) {
                freedBytes += file.length()
            }
        }
        return freedBytes
    }

    fun missingRuntimePermissions(): List<String> {
        val required = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )
        return required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun exportToCsv(): File {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val csvFile = File(exportsDir, "mitra_history_${System.currentTimeMillis()}.csv")
        val calls = repository.allCallsFlow.first()
        val messages = repository.allMessagesFlow.first()

        val sb = StringBuilder()
        sb.appendLine("type,contact,direction_or_sender,detail,dtmf_or_status,timestamp")
        calls.forEach { c ->
            val safeTranscript = c.transcript.replace("\"", "'").replace("\n", " ")
            sb.appendLine("CALL,\"${c.contactId}\",\"${c.direction}\",\"$safeTranscript\",\"${c.dtmfHistory}\",${c.timestamp}")
        }
        messages.forEach { m ->
            val safeBody = m.message.replace("\"", "'").replace("\n", " ")
            val sender = if (m.isFromAi) "MITRA" else "CONTACT"
            sb.appendLine("SMS,\"${m.contactId}\",\"$sender\",\"$safeBody\",\"${m.deliveryStatus}\",${m.timestamp}")
        }
        csvFile.writeText(sb.toString())
        return csvFile
    }

    suspend fun autoMuteSpam(enabled: Boolean) {
        preferencesRepository.setAutoMuteSpamEnabled(enabled)
    }

    suspend fun transcriptSummarization(contactId: String): String {
        val compressed = repository.buildCompressedContext(contactId, 1200)
        return aiConversationManager.generateReply(
            prompt = "Summarize this contact's recent calls and SMS in 2 concise bullet points.",
            contact = contactId,
            isCall = false
        ).text.ifBlank { compressed.formattedContext }
    }

    suspend fun blockUnknownNumbers(enabled: Boolean) {
        preferencesRepository.setBlockUnknownEnabled(enabled)
    }

    suspend fun whitelistContact(contact: String) {
        preferencesRepository.addWhitelistedContact(contact)
    }

    suspend fun vibrateOnRinging(enabled: Boolean) {
        preferencesRepository.setVibrateOnRingEnabled(enabled)
    }

    suspend fun setDoNotDisturb(enabled: Boolean) {
        preferencesRepository.setDndEnabled(enabled)
    }

    fun readAloudSms(sender: String, body: String) {
        voiceSessionController.speakText("SMS from $sender: $body")
    }

    fun detectPhishing(message: String): Boolean {
        return phishingAnalyzer.analyzeSms(message).isSuspicious
    }

    suspend fun autoReplySms(enabled: Boolean) {
        preferencesRepository.setAutoReplySmsEnabled(enabled)
    }

    suspend fun generateCallStats(): CallStatsSummary {
        val calls = repository.allCallsFlow.first()
        val messages = repository.allMessagesFlow.first()
        val battery = batteryMonitor.getBatteryState()
        val avgSec = if (calls.isEmpty()) 0L else calls.sumOf { it.durationSeconds } / calls.size
        return CallStatsSummary(
            totalCalls = calls.size,
            totalSms = messages.size,
            flaggedPhishingSms = messages.count { it.isPhishingFlagged },
            averageCallSeconds = avgSec,
            batteryLevel = battery.level,
            batteryTempCelsius = battery.temperatureCelsius
        )
    }

    suspend fun dailyReport(): String {
        val stats = generateCallStats()
        return "Mitra Daily Report: ${stats.totalCalls} calls (avg ${stats.averageCallSeconds}s), " +
            "${stats.totalSms} SMS (${stats.flaggedPhishingSms} phishing flagged), " +
            "Battery ${stats.batteryLevel.toInt()}% (${stats.batteryTempCelsius}°C)"
    }

    suspend fun optimizeBattery(lowBatteryThreshold: Int = 20, thermalCelsius: Float = 40f) {
        preferencesRepository.updatePowerThresholds(lowBatteryThreshold, thermalCelsius)
    }

    suspend fun updateLocalAiModel(modelPath: String): Result<Unit> {
        preferencesRepository.setLocalModelPath(modelPath)
        return localModelEngine.loadModelFromPath(modelPath)
    }

    suspend fun clearOldSms(days: Int = 30): Int {
        return repository.pruneOldSms(days)
    }

    suspend fun rotateApiKeys() {
        preferencesRepository.clearAllApiKeys()
    }

    suspend fun translateIncomingCall(transcript: String, targetLanguage: String = "Spanish"): String {
        return aiConversationManager.generateReply(
            prompt = "Translate the following call transcript accurately to $targetLanguage: $transcript",
            contact = null,
            isCall = true
        ).text
    }

    suspend fun themeColorPicker(themeName: String) {
        preferencesRepository.setAccentTheme(themeName)
    }

    suspend fun enableSpeechToText(enabled: Boolean) {
        preferencesRepository.setSpeechToTextEnabled(enabled)
    }

    suspend fun sendQuickSms(recipient: String, body: String): Result<String> {
        return smsDispatcher.sendSms(recipient, body, isFromAi = false)
    }
}
