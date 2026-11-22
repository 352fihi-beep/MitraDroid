package com.mitra.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class UserSettings(
    val groqApiKey: String = "",
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val mistralApiKey: String = "",
    val primaryProvider: String = "Groq",
    val localModelPath: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLocaleTag: String = "es-PR",
    val lowBatteryThreshold: Int = 20,
    val thermalThresholdCelsius: Float = 40.0f,
    val darkThemeEnabled: Boolean = true,
    val dynamicColorEnabled: Boolean = true,
    val accentTheme: String = "ElectricTeal",
    val autoReplySmsEnabled: Boolean = false,
    val phishingGuardEnabled: Boolean = true,
    val readAloudSmsEnabled: Boolean = false,
    val blockUnknownEnabled: Boolean = false,
    val autoMuteSpamEnabled: Boolean = true,
    val vibrateOnRingEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val speechToTextEnabled: Boolean = true,
    val whitelistedContacts: Set<String> = emptySet(),
    val blockedNumbers: Set<String> = emptySet(),
    val lastSyncTimestamp: Long = 0L
) {
    fun apiKeyFor(provider: String): String = when (provider) {
        "Groq" -> groqApiKey
        "Gemini" -> geminiApiKey
        "OpenRouter" -> openRouterApiKey
        "Mistral" -> mistralApiKey
        else -> ""
    }

    fun hasAnyCloudKey(): Boolean =
        groqApiKey.isNotBlank() || geminiApiKey.isNotBlank() ||
            openRouterApiKey.isNotBlank() || mistralApiKey.isNotBlank()
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val GROQ_KEY = stringPreferencesKey("groq_api_key")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        val OPENROUTER_KEY = stringPreferencesKey("openrouter_api_key")
        val MISTRAL_KEY = stringPreferencesKey("mistral_api_key")
        val PRIMARY_PROVIDER = stringPreferencesKey("primary_provider")
        val LOCAL_MODEL_PATH = stringPreferencesKey("local_model_path")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_LOCALE = stringPreferencesKey("tts_locale_tag")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val THERMAL_THRESHOLD = floatPreferencesKey("thermal_threshold_celsius")
        val DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val AUTO_REPLY_SMS = booleanPreferencesKey("auto_reply_sms_enabled")
        val PHISHING_GUARD = booleanPreferencesKey("phishing_guard_enabled")
        val READ_ALOUD_SMS = booleanPreferencesKey("read_aloud_sms_enabled")
        val BLOCK_UNKNOWN = booleanPreferencesKey("block_unknown_enabled")
        val AUTO_MUTE_SPAM = booleanPreferencesKey("auto_mute_spam_enabled")
        val VIBRATE_ON_RING = booleanPreferencesKey("vibrate_on_ring_enabled")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        val STT_ENABLED = booleanPreferencesKey("speech_to_text_enabled")
        val WHITELISTED_CONTACTS = stringSetPreferencesKey("whitelisted_contacts")
        val BLOCKED_NUMBERS = stringSetPreferencesKey("blocked_numbers")
    }

    val settingsFlow: Flow<UserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserSettings(
                groqApiKey = prefs[Keys.GROQ_KEY] ?: "",
                geminiApiKey = prefs[Keys.GEMINI_KEY] ?: "",
                openRouterApiKey = prefs[Keys.OPENROUTER_KEY] ?: "",
                mistralApiKey = prefs[Keys.MISTRAL_KEY] ?: "",
                primaryProvider = prefs[Keys.PRIMARY_PROVIDER] ?: "Groq",
                localModelPath = prefs[Keys.LOCAL_MODEL_PATH] ?: "",
                ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f,
                ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
                ttsLocaleTag = prefs[Keys.TTS_LOCALE] ?: "es-PR",
                lowBatteryThreshold = prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20,
                thermalThresholdCelsius = prefs[Keys.THERMAL_THRESHOLD] ?: 40.0f,
                darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
                accentTheme = prefs[Keys.ACCENT_THEME] ?: "ElectricTeal",
                autoReplySmsEnabled = prefs[Keys.AUTO_REPLY_SMS] ?: false,
                phishingGuardEnabled = prefs[Keys.PHISHING_GUARD] ?: true,
                readAloudSmsEnabled = prefs[Keys.READ_ALOUD_SMS] ?: false,
                blockUnknownEnabled = prefs[Keys.BLOCK_UNKNOWN] ?: false,
                autoMuteSpamEnabled = prefs[Keys.AUTO_MUTE_SPAM] ?: true,
                vibrateOnRingEnabled = prefs[Keys.VIBRATE_ON_RING] ?: true,
                dndEnabled = prefs[Keys.DND_ENABLED] ?: false,
                speechToTextEnabled = prefs[Keys.STT_ENABLED] ?: true,
                whitelistedContacts = prefs[Keys.WHITELISTED_CONTACTS] ?: emptySet(),
                blockedNumbers = prefs[Keys.BLOCKED_NUMBERS] ?: emptySet()
            )
        }

    suspend fun getCurrentSettings(): UserSettings = settingsFlow.first()

    suspend fun saveProviderApiKey(provider: String, apiKey: String) {
        dataStore.edit { prefs ->
            when (provider) {
                "Groq" -> prefs[Keys.GROQ_KEY] = apiKey.trim()
                "Gemini" -> prefs[Keys.GEMINI_KEY] = apiKey.trim()
                "OpenRouter" -> prefs[Keys.OPENROUTER_KEY] = apiKey.trim()
                "Mistral" -> prefs[Keys.MISTRAL_KEY] = apiKey.trim()
            }
        }
    }

    suspend fun setPrimaryProvider(provider: String) {
        dataStore.edit { it[Keys.PRIMARY_PROVIDER] = provider }
    }

    suspend fun setLocalModelPath(path: String) {
        dataStore.edit { it[Keys.LOCAL_MODEL_PATH] = path }
    }

    suspend fun updateTtsSettings(rate: Float, pitch: Float, localeTag: String) {
        dataStore.edit { prefs ->
            prefs[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f)
            prefs[Keys.TTS_PITCH] = pitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.TTS_LOCALE] = localeTag
        }
    }

    suspend fun updatePowerThresholds(lowBattery: Int, thermalCelsius: Float) {
        dataStore.edit { prefs ->
            prefs[Keys.LOW_BATTERY_THRESHOLD] = lowBattery.coerceIn(5, 50)
            prefs[Keys.THERMAL_THRESHOLD] = thermalCelsius.coerceIn(30f, 55f)
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAccentTheme(theme: String) {
        dataStore.edit { it[Keys.ACCENT_THEME] = theme }
    }

    suspend fun setAutoReplySmsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_REPLY_SMS] = enabled }
    }

    suspend fun setPhishingGuardEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PHISHING_GUARD] = enabled }
    }

    suspend fun setReadAloudSmsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.READ_ALOUD_SMS] = enabled }
    }

    suspend fun setBlockUnknownEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BLOCK_UNKNOWN] = enabled }
    }

    suspend fun setAutoMuteSpamEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_MUTE_SPAM] = enabled }
    }

    suspend fun setVibrateOnRingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATE_ON_RING] = enabled }
    }

    suspend fun setDndEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DND_ENABLED] = enabled }
    }

    suspend fun setSpeechToTextEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.STT_ENABLED] = enabled }
    }

    suspend fun addWhitelistedContact(contact: String) {
        val cleaned = contact.trim()
        if (cleaned.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.WHITELISTED_CONTACTS] ?: emptySet()
            prefs[Keys.WHITELISTED_CONTACTS] = current + cleaned
        }
    }

    suspend fun removeWhitelistedContact(contact: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.WHITELISTED_CONTACTS] ?: emptySet()
            prefs[Keys.WHITELISTED_CONTACTS] = current - contact
        }
    }

    suspend fun clearAllApiKeys() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.GROQ_KEY)
            prefs.remove(Keys.GEMINI_KEY)
            prefs.remove(Keys.OPENROUTER_KEY)
            prefs.remove(Keys.MISTRAL_KEY)
        }
    }
}
