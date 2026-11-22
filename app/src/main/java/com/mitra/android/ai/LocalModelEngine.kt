package com.mitra.android.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class InferenceMode {
    CLOUD,
    MEDIAPIPE,
    RULE_FALLBACK
}

sealed interface LocalModelStatus {
    data object RuleFallbackReady : LocalModelStatus
    data class Loading(val modelName: String) : LocalModelStatus
    data class MediaPipeReady(val modelName: String, val fileSizeMb: Long, val path: String) : LocalModelStatus
    data class Error(val message: String) : LocalModelStatus
}

data class LocalInferenceResult(
    val text: String,
    val mode: InferenceMode,
    val latencyMs: Long
)

/**
 * Local Quantized Model Engine supporting MediaPipe GenAI LlmInference (.task / .bin)
 * with deterministic bilingual on-device rule fallback when no model weights are imported
 * or when native delegates are unavailable.
 */
class LocalModelEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    private val _statusFlow = MutableStateFlow<LocalModelStatus>(LocalModelStatus.RuleFallbackReady)
    val statusFlow: StateFlow<LocalModelStatus> = _statusFlow.asStateFlow()

    val isMediaPipeModelLoaded: Boolean
        get() = llmInference != null

    suspend fun importModelFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveFileName(uri) ?: "mitra_local_model.task"
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val destFile = File(modelsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open selected model file.")

            loadModelFromPath(destFile.absolutePath).getOrThrow()
            destFile.absolutePath
        }
    }

    suspend fun loadModelFromPath(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (modelPath.isBlank()) {
            unloadModel()
            return@withContext Result.success(Unit)
        }

        val file = File(modelPath)
        if (!file.exists() || !file.isFile) {
            _statusFlow.value = LocalModelStatus.Error("Model file not found at $modelPath")
            return@withContext Result.failure(IllegalArgumentException("Model file not found: $modelPath"))
        }

        _statusFlow.value = LocalModelStatus.Loading(file.name)
        return@withContext try {
            llmInference?.close()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = file.absolutePath
            val sizeMb = (file.length() / (1024L * 1024L)).coerceAtLeast(1L)
            _statusFlow.value = LocalModelStatus.MediaPipeReady(file.name, sizeMb, file.absolutePath)
            Log.d("LocalModelEngine", "MediaPipe LlmInference loaded: ${file.name} (${sizeMb}MB)")
            Result.success(Unit)
        } catch (t: Throwable) {
            llmInference = null
            loadedModelPath = null
            _statusFlow.value = LocalModelStatus.Error(
                t.message ?: "MediaPipe native initialization unavailable; using rule fallback."
            )
            Log.w("LocalModelEngine", "MediaPipe load failed, retaining rule fallback", t)
            Result.failure(t)
        }
    }

    fun unloadModel() {
        runCatching { llmInference?.close() }
        llmInference = null
        loadedModelPath = null
        _statusFlow.value = LocalModelStatus.RuleFallbackReady
    }

    suspend fun generateResponseLocally(
        prompt: String,
        contactContext: String = "",
        isLowPower: Boolean = false
    ): LocalInferenceResult {
        val start = System.currentTimeMillis()
        val activeLlm = llmInference
        if (activeLlm != null) {
            val mediapipeResult = withContext(Dispatchers.IO) {
                try {
                    val promptWithContext = buildString {
                        append("You are Mitra, a warm Puerto Rico friendly AI assistant.\n")
                        if (isLowPower) append("Keep answer under 2 sentences for battery conservation.\n")
                        if (contactContext.isNotBlank()) append("Context:\n$contactContext\n\n")
                        append("User: $prompt\nMitra:")
                    }
                    activeLlm.generateResponse(promptWithContext)?.trim()
                } catch (t: Throwable) {
                    Log.w("LocalModelEngine", "MediaPipe generation fell back to deterministic engine", t)
                    null
                }
            }
            if (!mediapipeResult.isNullOrBlank()) {
                return LocalInferenceResult(
                    text = mediapipeResult,
                    mode = InferenceMode.MEDIAPIPE,
                    latencyMs = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                )
            }
        }

        val fallbackText = buildDeterministicResponse(prompt, contactContext, isLowPower)
        return LocalInferenceResult(
            text = fallbackText,
            mode = InferenceMode.RULE_FALLBACK,
            latencyMs = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        )
    }

    internal fun buildDeterministicResponse(
        prompt: String,
        contactContext: String,
        isLowPower: Boolean
    ): String {
        val lower = prompt.lowercase()
        val powerTag = if (isLowPower) "[Low-Power Edge] " else "[On-Device Edge] "

        val ivrMatch = Regex("(?:press|oprima|dial|seleccione|marque)\\s+([0-9*#]|one|two|three|uno|dos|tres)")
            .find(lower)
        if (ivrMatch != null) {
            val target = ivrMatch.groupValues[1]
            return "${powerTag}IVR option '$target' detected. Ready to dispatch DTMF tone automatically."
        }

        if (listOf("hola", "hello", "hi", "buenos", "saludos").any { lower.contains(it) }) {
            return "${powerTag}Saludos! Soy Mitra en modo local. How can I help with your call or message?"
        }

        if (listOf("appointment", "cita", "schedule", "confirm", "hora").any { lower.contains(it) }) {
            return "${powerTag}Received your scheduling note. I've saved it to contact memory and can reply to confirm."
        }

        if (listOf("urgent", "emergency", "help", "ayuda", "callback").any { lower.contains(it) }) {
            return "${powerTag}Priority message noted. I'll surface this right away and keep a concise transcript ready."
        }

        val contextHint = if (contactContext.contains("Recent SMS Thread")) {
            " Contact history is synced locally."
        } else {
            ""
        }
        return "${powerTag}Processed locally: \"${prompt.take(90)}\".$contextHint Add a cloud API key in Settings or import a .task model in Voice Studio for generative replies."
    }

    private fun resolveFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
        }
    }
}
