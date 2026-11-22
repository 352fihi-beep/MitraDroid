package com.mitra.android

import android.content.Context
import android.telecom.Connection
import android.util.Log
import com.mitra.android.ai.InferenceMode
import com.mitra.android.ai.LocalModelEngine
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.repository.MitraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AIResponseResult(
    val text: String,
    val providerUsed: String,
    val mode: InferenceMode,
    val latencyMs: Long,
    val usedFallback: Boolean,
    val compressedTokens: Int
)

@Singleton
class AIConversationManager @Inject constructor(
    private val llmService: LLMService,
    private val batteryMonitor: BatteryMonitor,
    private val localModelEngine: LocalModelEngine,
    private val preferencesRepository: UserPreferencesRepository,
    private val repository: MitraRepository
) {
    @Volatile
    private var currentProvider = "Groq"

    suspend fun getAIResponse(prompt: String, contact: String?, isCall: Boolean = true): String {
        return generateReply(prompt = prompt, contact = contact, isCall = isCall).text
    }

    suspend fun generateReply(
        prompt: String,
        contact: String?,
        isCall: Boolean = true,
        forceEdge: Boolean = false
    ): AIResponseResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val settings = preferencesRepository.getCurrentSettings()
        val batteryState = batteryMonitor.getBatteryState()

        val isOverheating = batteryState.isOverheating(settings.thermalThresholdCelsius)
        val isLowPower = batteryState.isLowPower(settings.lowBatteryThreshold)
        val tokenBudget = when {
            isOverheating -> 600
            isLowPower -> 1500
            else -> 6000
        }

        val compressed = repository.buildCompressedContext(contact, tokenBudget)

        // Ensure saved local MediaPipe model is loaded if configured and not yet loaded
        if (settings.localModelPath.isNotBlank() && !localModelEngine.isMediaPipeModelLoaded) {
            localModelEngine.loadModelFromPath(settings.localModelPath)
        }

        val shouldUseEdgeOnly = forceEdge || isOverheating || isLowPower || !settings.hasAnyCloudKey()
        if (shouldUseEdgeOnly) {
            val local = localModelEngine.generateResponseLocally(
                prompt = prompt,
                contactContext = compressed.formattedContext,
                isLowPower = isLowPower || isOverheating
            )
            return@withContext AIResponseResult(
                text = local.text,
                providerUsed = if (local.mode == InferenceMode.MEDIAPIPE) "MediaPipe Local" else "Edge Rule Engine",
                mode = local.mode,
                latencyMs = local.latencyMs,
                usedFallback = true,
                compressedTokens = compressed.estimatedTokens
            )
        }

        val systemExt = when {
            isOverheating -> "[THERMAL GUARD: Be ultra-concise.]"
            isLowPower -> "[LOW POWER: Max 1024 tokens.]"
            else -> ""
        }

        val modeInstruction = if (isCall) {
            "Calls: Master IVR navigation, DTMF digits, and concise spoken replies."
        } else {
            "SMS: Short, natural, helpful replies."
        }

        val systemPrompt = """
            You are Mitra — Puerto Rico friendly, warm, and efficient.
            Match the user's language (English or Spanish) naturally.
            $modeInstruction
            $systemExt
            Memory Context:
            ${compressed.formattedContext}
        """.trimIndent()

        val orderedProviders = buildList {
            val primary = settings.primaryProvider
            if (settings.apiKeyFor(primary).isNotBlank()) add(primary)
            LLMConfig.providers.keys.forEach { provider ->
                if (provider != primary && settings.apiKeyFor(provider).isNotBlank()) {
                    add(provider)
                }
            }
        }

        for (provider in orderedProviders) {
            val endpoint = LLMConfig.providers[provider] ?: continue
            val apiKey = settings.apiKeyFor(provider)
            val model = LLMConfig.defaultModels[provider] ?: continue
            try {
                currentProvider = provider
                val response = llmService.getResponse(
                    url = endpoint,
                    auth = "Bearer $apiKey",
                    body = LLMRequest(
                        model = model,
                        messages = listOf(
                            Message(role = "system", content = systemPrompt),
                            Message(role = "user", content = prompt)
                        ),
                        max_tokens = if (isLowPower) 1024 else 4096
                    )
                )
                val content = response.choices.firstOrNull()?.message?.content?.trim()
                if (!content.isNullOrBlank()) {
                    return@withContext AIResponseResult(
                        text = content,
                        providerUsed = provider,
                        mode = InferenceMode.CLOUD,
                        latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L),
                        usedFallback = provider != settings.primaryProvider,
                        compressedTokens = compressed.estimatedTokens
                    )
                }
            } catch (e: Exception) {
                Log.w("AIConversationManager", "Provider $provider failed, trying failover", e)
            }
        }

        // Cloud exhausted or failed -> seamless local fallback
        val local = localModelEngine.generateResponseLocally(
            prompt = prompt,
            contactContext = compressed.formattedContext,
            isLowPower = isLowPower || isOverheating
        )
        AIResponseResult(
            text = local.text,
            providerUsed = if (local.mode == InferenceMode.MEDIAPIPE) "MediaPipe Fallback" else "Edge Rule Fallback",
            mode = local.mode,
            latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L),
            usedFallback = true,
            compressedTokens = compressed.estimatedTokens
        )
    }

    suspend fun verifyProviderKey(provider: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API key cannot be blank"))
            }
            val endpoint = LLMConfig.providers[provider]
                ?: return@withContext Result.failure(IllegalArgumentException("Unknown provider: $provider"))
            val model = LLMConfig.defaultModels[provider]
                ?: return@withContext Result.failure(IllegalArgumentException("No default model for: $provider"))
            runCatching {
                val response = llmService.getResponse(
                    url = endpoint,
                    auth = "Bearer ${apiKey.trim()}",
                    body = LLMRequest(
                        model = model,
                        messages = listOf(Message(role = "user", content = "Reply with OK")),
                        max_tokens = 16
                    )
                )
                response.choices.firstOrNull()?.message?.content?.trim()?.ifBlank { "Verified" }
                    ?: "Verified"
            }
        }

    fun handleCall(context: Context, connection: Connection) {
        Log.d("AIConversationManager", "Live Telecom connection active; voice loop orchestrated by VoiceSessionController.")
    }

    fun extractIvrTone(transcript: String): Char? {
        val normalizedTranscript = transcript.lowercase().replace(Regex("\\s+"), " ")

        val digitRegex = Regex("(?:press|oprima|enter|dial|seleccione|marque|type)\\s+([0-9*#])(?:\\s+(?:for|para|to|if)|\\b)")
        val match = digitRegex.find(normalizedTranscript)
        if (match != null) {
            return match.groupValues[1].first()
        }

        val wordRegex = Regex("(?:press|oprima|enter|dial|seleccione|marque|type)\\s+(one|two|three|four|five|six|seven|eight|nine|zero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|cero|pound|star|asterisk|hash|libra|estrella|asterisco)(?:\\s+(?:for|para|to|if)|\\b)")
        val wordMatch = wordRegex.find(normalizedTranscript) ?: return null
        return when (wordMatch.groupValues[1]) {
            "one", "uno" -> '1'
            "two", "dos" -> '2'
            "three", "tres" -> '3'
            "four", "cuatro" -> '4'
            "five", "cinco" -> '5'
            "six", "seis" -> '6'
            "seven", "siete" -> '7'
            "eight", "ocho" -> '8'
            "nine", "nueve" -> '9'
            "zero", "cero" -> '0'
            "pound", "hash", "libra" -> '#'
            "star", "asterisk", "estrella", "asterisco" -> '*'
            else -> null
        }
    }

    fun detectAndSendIVR(transcript: String, connection: Connection?): Char? {
        val tone = extractIvrTone(transcript) ?: return null
        Log.d("AIConversationManager", "IVR prompt detected, sending DTMF: $tone")
        if (connection != null) {
            sendDTMF(connection, tone)
        }
        return tone
    }

    fun sendDTMF(connection: Connection, tone: Char) {
        connection.onPlayDtmfTone(tone)
    }
}
