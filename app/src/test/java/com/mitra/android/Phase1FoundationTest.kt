package com.mitra.android

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.database.AppDatabase
import com.mitra.android.database.CallRecord
import com.mitra.android.database.ContactMemory
import com.mitra.android.database.SmsMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase1FoundationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `app name resource matches MitraAndroid`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("MitraAndroid", appName)
    }

    @Test
    fun `room v2 entities persist and emit reactive flows`() = runTest {
        db.dao().insertMemory(
            ContactMemory(
                contactId = "+1-787-555-0142",
                displayName = "Elena",
                memorySummary = "Prefers Spanish replies",
                compressedTokenCount = 4
            )
        )
        db.smsDao().insertSms(
            SmsMessage(contactId = "+1-787-555-0142", isFromAi = false, message = "Hola!")
        )
        db.callRecordDao().insertCall(
            CallRecord(
                contactId = "+1-787-555-0142",
                direction = "INCOMING",
                durationSeconds = 42,
                transcript = "Caller: Press 2",
                dtmfHistory = "2",
                inferenceMode = "CLOUD"
            )
        )

        val memory = db.dao().getMemoryForContact("+1-787-555-0142")
        val smsList = db.smsDao().getSmsThread("+1-787-555-0142").first()
        val calls = db.callRecordDao().getAllCalls().first()

        assertEquals("Prefers Spanish replies", memory)
        assertEquals(1, smsList.size)
        assertEquals("Hola!", smsList.first().message)
        assertEquals(1, calls.size)
        assertEquals("2", calls.first().dtmfHistory)
    }

    @Test
    fun `datastore preferences save and restore api keys and qol flags`() = runTest {
        val testFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })
        val repo = UserPreferencesRepository(dataStore)

        repo.saveProviderApiKey("Groq", "gsk_test_123")
        repo.setPrimaryProvider("Gemini")
        repo.setAutoReplySmsEnabled(true)
        repo.addWhitelistedContact("+1-787-555-0100")

        val settings = repo.getCurrentSettings()
        assertEquals("gsk_test_123", settings.apiKeyFor("Groq"))
        assertEquals("Gemini", settings.primaryProvider)
        assertTrue(settings.autoReplySmsEnabled)
        assertTrue(settings.whitelistedContacts.contains("+1-787-555-0100"))
    }
}
