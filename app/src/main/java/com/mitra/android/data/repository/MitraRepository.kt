package com.mitra.android.data.repository

import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.local.UserSettings
import com.mitra.android.database.CallRecord
import com.mitra.android.database.CallRecordDao
import com.mitra.android.database.ContactMemory
import com.mitra.android.database.MemoryDao
import com.mitra.android.database.SmsDao
import com.mitra.android.database.SmsMessage
import com.mitra.android.domain.CompressedContext
import com.mitra.android.domain.MemoryCompressor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MitraRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val smsDao: SmsDao,
    private val callRecordDao: CallRecordDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val memoryCompressor: MemoryCompressor
) {
    val settingsFlow: Flow<UserSettings> = preferencesRepository.settingsFlow
    val allMessagesFlow: Flow<List<SmsMessage>> = smsDao.getAllMessages()
    val allCallsFlow: Flow<List<CallRecord>> = callRecordDao.getAllCalls()
    val allMemoriesFlow: Flow<List<ContactMemory>> = memoryDao.getAllMemories()

    fun getSmsThread(contactId: String): Flow<List<SmsMessage>> = smsDao.getSmsThread(contactId)

    suspend fun getCurrentSettings(): UserSettings = preferencesRepository.getCurrentSettings()

    suspend fun buildCompressedContext(contactId: String?, tokenBudget: Int): CompressedContext {
        if (contactId.isNullOrBlank()) {
            return CompressedContext("No active contact selected.", 6, 0, 0)
        }
        val memory = memoryDao.getMemoryForContact(contactId)
        val sms = smsDao.getSmsThread(contactId).first()
        val calls = callRecordDao.getCallsForContact(contactId).first()
        return memoryCompressor.compress(contactId, memory, sms, calls, tokenBudget)
    }

    suspend fun recordSms(
        contactId: String,
        message: String,
        isFromAi: Boolean,
        isPhishingFlagged: Boolean = false,
        deliveryStatus: String = "RECEIVED"
    ): Long {
        val insertedId = smsDao.insertSms(
            SmsMessage(
                contactId = contactId,
                isFromAi = isFromAi,
                message = message,
                isPhishingFlagged = isPhishingFlagged,
                deliveryStatus = deliveryStatus
            )
        )
        refreshContactMemory(contactId)
        return insertedId
    }

    suspend fun recordCall(
        contactId: String,
        direction: String,
        durationSeconds: Long,
        transcript: String,
        dtmfHistory: String,
        inferenceMode: String
    ): Long {
        val id = callRecordDao.insertCall(
            CallRecord(
                contactId = contactId,
                direction = direction,
                durationSeconds = durationSeconds,
                transcript = transcript,
                dtmfHistory = dtmfHistory,
                inferenceMode = inferenceMode
            )
        )
        refreshContactMemory(contactId)
        return id
    }

    suspend fun refreshContactMemory(contactId: String) {
        val existing = memoryDao.getContactMemoryEntity(contactId)
        val sms = smsDao.getSmsThread(contactId).first()
        val calls = callRecordDao.getCallsForContact(contactId).first()
        val synthesized = memoryCompressor.synthesizeContactMemory(
            contactId = contactId,
            displayName = existing?.displayName ?: contactId,
            smsHistory = sms,
            callHistory = calls
        )
        memoryDao.insertMemory(synthesized)
    }

    suspend fun pruneOldSms(olderThanDays: Int = 30): Int {
        val cutoff = System.currentTimeMillis() - olderThanDays * 24L * 60L * 60L * 1000L
        return smsDao.deleteOldSmsBefore(cutoff)
    }

    suspend fun clearAllHistory() {
        smsDao.clearAllSms()
        callRecordDao.clearAllCalls()
        memoryDao.clearAllMemories()
    }

    suspend fun seedDemoThreadIfEmpty() {
        if (smsDao.getRecentMessagesSnapshot(1).isEmpty()) {
            recordSms(
                contactId = "+1-787-555-0142",
                message = "Hola Mitra! Can you confirm my appointment for tomorrow at 10 AM?",
                isFromAi = false
            )
            recordSms(
                contactId = "+1-787-555-0142",
                message = "Claro! I logged your 10:00 AM appointment reminder and am ready to assist on calls or SMS.",
                isFromAi = true,
                deliveryStatus = "SENT"
            )
        }
    }
}
