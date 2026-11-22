package com.mitra.android

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mitra.android.ai.LocalModelEngine
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.local.UserSettings
import com.mitra.android.data.repository.MitraRepository
import com.mitra.android.database.AppDatabase
import com.mitra.android.domain.MemoryCompressor
import com.mitra.android.telephony.PhishingAndSpamAnalyzer
import com.mitra.android.telephony.SmsDispatcher
import com.mitra.android.telephony.VoiceSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
class Phase3TelephonyAndSmsTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: MitraRepository
    private lateinit var aiManager: AIConversationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsFile = File(context.filesDir, "phase3_prefs_${System.nanoTime()}.preferences_pb")
        prefsRepo = UserPreferencesRepository(PreferenceDataStoreFactory.create { prefsFile })
        repository = MitraRepository(db.dao(), db.smsDao(), db.callRecordDao(), prefsRepo, MemoryCompressor())
        val fakeLlm = object : LLMService {
            override suspend fun getResponse(url: String, auth: String, body: LLMRequest): LLMResponse {
                return LLMResponse(listOf(Choice(Message("assistant", "OK"))))
            }
        }
        aiManager = AIConversationManager(
            llmService = fakeLlm,
            batteryMonitor = BatteryMonitor(context),
            localModelEngine = LocalModelEngine(context),
            preferencesRepository = prefsRepo,
            repository = repository
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `bilingual ivr detection extracts digits and words and dispatches dtmf on connection`() {
        val connection = MitraConnection()

        assertEquals('2', aiManager.detectAndSendIVR("Please press 2 for customer support.", connection))
        assertEquals('3', aiManager.detectAndSendIVR("Oprima tres para hablar con un representante.", connection))
        assertEquals('#', aiManager.detectAndSendIVR("Dial pound to confirm your entry.", connection))
        assertEquals('*', aiManager.detectAndSendIVR("Marque asterisco para regresar.", connection))
        assertEquals(listOf('2', '3', '#', '*'), connection.dtmfHistory)
    }

    @Test
    fun `phishing analyzer flags high risk lures and honors call whitelist`() {
        val analyzer = PhishingAndSpamAnalyzer()

        val suspicious = analyzer.analyzeSms("Urgent action: verify your account immediately at http://bit.ly/bank-otp")
        val benign = analyzer.analyzeSms("Hey! Running 10 minutes late for coffee.")

        assertTrue(suspicious.isSuspicious)
        assertTrue(suspicious.score >= 50)
        assertFalse(benign.isSuspicious)

        val settings = UserSettings(
            blockUnknownEnabled = true,
            whitelistedContacts = setOf("787-555-0142"),
            blockedNumbers = setOf("999-0000")
        )
        assertFalse(analyzer.shouldBlockOrMuteCall("+1-787-555-0142", settings))
        assertTrue(analyzer.shouldBlockOrMuteCall("999-0000", settings))
        assertTrue(analyzer.shouldBlockOrMuteCall("1234", settings))
    }

    @Test
    fun `qol campaign exports csv and computes stats cleanly`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = VoiceSessionController(context, aiManager, repository, prefsRepo, scope)
        val dispatcher = SmsDispatcher(context, repository)
        val qol = QualityOfLifeCampaign(
            context = context,
            preferencesRepository = prefsRepo,
            repository = repository,
            batteryMonitor = BatteryMonitor(context),
            localModelEngine = LocalModelEngine(context),
            phishingAnalyzer = PhishingAndSpamAnalyzer(),
            aiConversationManager = aiManager,
            smsDispatcher = dispatcher,
            voiceSessionController = controller
        )

        repository.recordSms("+1-787-555-0142", "Urgent verify your account http://bit.ly/x", isFromAi = false, isPhishingFlagged = true)
        repository.recordCall("+1-787-555-0142", "OUTGOING", 30L, "Caller: Press 1", "1", "RULE_FALLBACK")

        val stats = qol.generateCallStats()
        assertEquals(1, stats.totalCalls)
        assertEquals(1, stats.totalSms)
        assertEquals(1, stats.flaggedPhishingSms)

        val csv = qol.exportToCsv()
        val csvContent = csv.readText()
        assertTrue(csvContent.contains("CALL"))
        assertTrue(csvContent.contains("SMS"))
        assertTrue(csvContent.contains("+1-787-555-0142"))
    }

    @Test
    fun `sms receiver ignores non-sms intents safely`() {
        val receiver = SmsReceiver()
        receiver.onReceive(context, Intent("com.example.OTHER_ACTION"))
        // Should return safely without crashing when action is not SMS_RECEIVED
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION, "android.provider.Telephony.SMS_RECEIVED")
    }
}
