package com.mitra.android

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mitra.android.ai.InferenceMode
import com.mitra.android.ai.LocalModelEngine
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.repository.MitraRepository
import com.mitra.android.database.AppDatabase
import com.mitra.android.database.CallRecord
import com.mitra.android.database.SmsMessage
import com.mitra.android.domain.MemoryCompressor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2HybridAiTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: MitraRepository
    private lateinit var localModelEngine: LocalModelEngine
    private lateinit var batteryMonitor: BatteryMonitor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsFile = File(context.filesDir, "phase2_prefs_${System.nanoTime()}.preferences_pb")
        prefsRepo = UserPreferencesRepository(PreferenceDataStoreFactory.create { prefsFile })
        val compressor = MemoryCompressor()
        repository = MitraRepository(
            memoryDao = db.dao(),
            smsDao = db.smsDao(),
            callRecordDao = db.callRecordDao(),
            preferencesRepository = prefsRepo,
            memoryCompressor = compressor
        )
        localModelEngine = LocalModelEngine(context)
        batteryMonitor = BatteryMonitor(context)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `memory compressor enforces token budget on large threads`() {
        val compressor = MemoryCompressor()
        val messages = (1..40).map { idx ->
            SmsMessage(
                id = idx,
                contactId = "contact-1",
                isFromAi = idx % 2 == 0,
                message = "Message number $idx discussing scheduling, billing, and follow up details for tomorrow morning.",
                timestamp = idx * 1000L
            )
        }
        val calls = listOf(
            CallRecord(
                id = 1,
                contactId = "contact-1",
                direction = "OUTGOING",
                durationSeconds = 95,
                transcript = "Press 1 for English, then confirmed Tuesday appointment.",
                dtmfHistory = "1",
                inferenceMode = "CLOUD"
            )
        )

        val compressed = compressor.compress(
            contactId = "contact-1",
            existingSummary = "VIP patient in San Juan.",
            smsHistory = messages,
            callHistory = calls,
            tokenBudget = 300
        )

        assertTrue(compressed.estimatedTokens <= 340)
        assertTrue(compressed.includedSmsCount < messages.size)
        assertTrue(compressed.formattedContext.contains("VIP patient in San Juan"))
    }

    @Test
    fun `hybrid router uses edge rule fallback when no cloud keys configured`() = runTest {
        val fakeService = object : LLMService {
            override suspend fun getResponse(url: String, auth: String, body: LLMRequest): LLMResponse {
                error("Should not hit cloud without key")
            }
        }
        val manager = AIConversationManager(
            llmService = fakeService,
            batteryMonitor = batteryMonitor,
            localModelEngine = localModelEngine,
            preferencesRepository = prefsRepo,
            repository = repository
        )

        val result = manager.generateReply("Hola Mitra, confirm my appointment", "+1-787-555-0142")
        assertEquals(InferenceMode.RULE_FALLBACK, result.mode)
        assertTrue(result.usedFallback)
        assertTrue(result.text.contains("Mitra") || result.text.contains("scheduling"))
    }

    @Test
    fun `hybrid router fails over from primary provider to backup cloud provider`() = runTest {
        prefsRepo.saveProviderApiKey("Groq", "bad-groq-key")
        prefsRepo.saveProviderApiKey("Gemini", "good-gemini-key")
        prefsRepo.setPrimaryProvider("Groq")

        val fakeService = object : LLMService {
            override suspend fun getResponse(url: String, auth: String, body: LLMRequest): LLMResponse {
                if (url.contains("groq")) {
                    throw RuntimeException("429 Rate Limit")
                }
                return LLMResponse(
                    choices = listOf(Choice(Message(role = "assistant", content = "Saludos desde Gemini!")))
                )
            }
        }

        val manager = AIConversationManager(
            llmService = fakeService,
            batteryMonitor = batteryMonitor,
            localModelEngine = localModelEngine,
            preferencesRepository = prefsRepo,
            repository = repository
        )

        val result = manager.generateReply("Hello!", "+1-787-555-0142")
        assertEquals(InferenceMode.CLOUD, result.mode)
        assertEquals("Gemini", result.providerUsed)
        assertTrue(result.usedFallback)
        assertEquals("Saludos desde Gemini!", result.text)
    }

    @Test
    fun `battery monitor forces edge fallback when battery critically low`() = runTest {
        prefsRepo.saveProviderApiKey("Groq", "valid-key")
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(BatteryManager.EXTRA_LEVEL, 12)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
                putExtra(BatteryManager.EXTRA_TEMPERATURE, 310)
            }
        )

        var cloudCalled = false
        val fakeService = object : LLMService {
            override suspend fun getResponse(url: String, auth: String, body: LLMRequest): LLMResponse {
                cloudCalled = true
                return LLMResponse(listOf(Choice(Message("assistant", "Cloud"))))
            }
        }

        val manager = AIConversationManager(
            llmService = fakeService,
            batteryMonitor = batteryMonitor,
            localModelEngine = localModelEngine,
            preferencesRepository = prefsRepo,
            repository = repository
        )

        val result = manager.generateReply("Oprima 2 para español", null)
        assertFalse(cloudCalled)
        assertEquals(InferenceMode.RULE_FALLBACK, result.mode)
        assertTrue(result.text.contains("Low-Power Edge"))
    }
}
