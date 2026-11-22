package com.mitra.android.domain

import com.mitra.android.database.CallRecord
import com.mitra.android.database.ContactMemory
import com.mitra.android.database.SmsMessage
import javax.inject.Inject
import javax.inject.Singleton

data class CompressedContext(
    val formattedContext: String,
    val estimatedTokens: Int,
    val includedSmsCount: Int,
    val includedCallCount: Int
)

@Singleton
class MemoryCompressor @Inject constructor() {

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }

    fun compress(
        contactId: String,
        existingSummary: String?,
        smsHistory: List<SmsMessage>,
        callHistory: List<CallRecord>,
        tokenBudget: Int
    ): CompressedContext {
        val safeBudget = tokenBudget.coerceAtLeast(128)
        val sections = mutableListOf<String>()
        var usedTokens = 0

        val summaryHeader = "Contact ($contactId) Summary: ${existingSummary?.take(500) ?: "New contact"}"
        sections += summaryHeader
        usedTokens += estimateTokens(summaryHeader)

        val selectedSms = mutableListOf<SmsMessage>()
        for (msg in smsHistory.sortedByDescending { it.timestamp }) {
            val speaker = if (msg.isFromAi) "Mitra" else "Contact"
            val line = "$speaker: ${msg.message.take(280)}"
            val lineTokens = estimateTokens(line)
            if (usedTokens + lineTokens > (safeBudget * 0.75).toInt()) break
            selectedSms += msg
            usedTokens += lineTokens
        }

        if (selectedSms.isNotEmpty()) {
            val threadText = selectedSms
                .sortedBy { it.timestamp }
                .joinToString("\n") { msg ->
                    val speaker = if (msg.isFromAi) "Mitra" else "Contact"
                    val flag = if (msg.isPhishingFlagged) " [FLAGGED]" else ""
                    "$speaker$flag: ${msg.message.take(280)}"
                }
            sections += "Recent SMS Thread:\n$threadText"
        }

        val selectedCalls = mutableListOf<CallRecord>()
        for (call in callHistory.sortedByDescending { it.timestamp }) {
            val dtmfNote = if (call.dtmfHistory.isNotBlank()) " [DTMF: ${call.dtmfHistory}]" else ""
            val snippet = call.transcript.take(200).ifBlank { "No transcript" }
            val line = "${call.direction} (${call.durationSeconds}s)$dtmfNote: $snippet"
            val lineTokens = estimateTokens(line)
            if (usedTokens + lineTokens > safeBudget) break
            selectedCalls += call
            usedTokens += lineTokens
        }

        if (selectedCalls.isNotEmpty()) {
            val callsText = selectedCalls.joinToString("\n") { call ->
                val dtmfNote = if (call.dtmfHistory.isNotBlank()) " [DTMF: ${call.dtmfHistory}]" else ""
                "- ${call.direction} ${call.durationSeconds}s$dtmfNote: ${call.transcript.take(200)}"
            }
            sections += "Recent Call Memory:\n$callsText"
        }

        val combined = sections.joinToString("\n\n")
        return CompressedContext(
            formattedContext = combined,
            estimatedTokens = estimateTokens(combined),
            includedSmsCount = selectedSms.size,
            includedCallCount = selectedCalls.size
        )
    }

    fun synthesizeContactMemory(
        contactId: String,
        displayName: String,
        smsHistory: List<SmsMessage>,
        callHistory: List<CallRecord>
    ): ContactMemory {
        val totalMessages = smsHistory.size
        val totalCalls = callHistory.size
        val flaggedCount = smsHistory.count { it.isPhishingFlagged }
        val lastSms = smsHistory.maxByOrNull { it.timestamp }?.message?.take(120)
        val lastDtmf = callHistory.firstOrNull { it.dtmfHistory.isNotBlank() }?.dtmfHistory

        val summaryParts = buildList {
            add("Threads: $totalMessages SMS, $totalCalls calls.")
            if (flaggedCount > 0) add("Phishing alerts: $flaggedCount.")
            if (!lastDtmf.isNullOrBlank()) add("Recent IVR tones: $lastDtmf.")
            if (!lastSms.isNullOrBlank()) add("Latest topic: \"$lastSms\"")
        }
        val summary = summaryParts.joinToString(" ")
        return ContactMemory(
            contactId = contactId,
            displayName = displayName.ifBlank { contactId },
            memorySummary = summary,
            compressedTokenCount = estimateTokens(summary),
            updatedAt = System.currentTimeMillis()
        )
    }
}
