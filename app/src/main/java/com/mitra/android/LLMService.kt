package com.mitra.android

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class LLMRequest(
    val model: String, 
    val messages: List<Message>, 
    val max_tokens: Int = 8192,
    val temperature: Double = 0.7,
    val top_p: Double = 0.95
)

data class LLMResponse(val choices: List<Choice>)
data class Choice(val message: Message)
data class Message(val role: String, val content: String)

interface LLMService {
    @POST
    suspend fun getResponse(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body body: LLMRequest
    ): LLMResponse
}

object LLMConfig {
    val providers = mapOf(
        "Groq" to "https://api.groq.com/openai/v1/chat/completions",
        "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
        "Mistral" to "https://api.mistral.ai/v1/chat/completions",
        "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    )
    
    val defaultModels = mapOf(
        "Groq" to "llama-3.3-70b-versatile",
        "Gemini" to "gemini-2.5-pro",
        "OpenRouter" to "anthropic/claude-3.5-sonnet",
        "Mistral" to "mistral-large-latest"
    )
    
    val contextWindows = mapOf(
        "Groq" to 128000,
        "Gemini" to 1000000,
        "OpenRouter" to 200000,
        "Mistral" to 128000
    )
}
