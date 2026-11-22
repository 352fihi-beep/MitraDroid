package com.mitra.android.telephony

import com.mitra.android.data.local.UserSettings
import javax.inject.Inject
import javax.inject.Singleton

data class PhishingAssessment(
    val isSuspicious: Boolean,
    val score: Int,
    val reasons: List<String>
)

@Singleton
class PhishingAndSpamAnalyzer @Inject constructor() {

    private val suspiciousUrlRegex = Regex("https?://\\S+|bit\\.ly/\\S+|tinyurl\\.com/\\S+|t\\.co/\\S+", RegexOption.IGNORE_CASE)
    private val urgentKeywords = listOf(
        "verify your account", "suspended", "urgent action", "otp", "wire transfer",
        "claim your prize", "irs", "bank alert", "cuentas suspendidas", "clave",
        "premio", "clic aqui", "click here", "update billing", "ssn"
    )

    fun analyzeSms(message: String): PhishingAssessment {
        val lower = message.lowercase()
        val reasons = mutableListOf<String>()
        var score = 0

        if (suspiciousUrlRegex.containsMatchIn(message)) {
            score += 40
            reasons += "Contains external or shortened link"
        }
        val matchedKeywords = urgentKeywords.filter { lower.contains(it) }
        if (matchedKeywords.isNotEmpty()) {
            score += (matchedKeywords.size * 25).coerceAtMost(60)
            reasons += "Urgency/credential keywords: ${matchedKeywords.take(3).joinToString(", ")}"
        }
        if (Regex("\\b\\d{4,8}\\b").containsMatchIn(lower) && (lower.contains("code") || lower.contains("código"))) {
            score += 15
            reasons += "Contains verification code request"
        }

        return PhishingAssessment(
            isSuspicious = score >= 50,
            score = score.coerceIn(0, 100),
            reasons = reasons
        )
    }

    fun shouldBlockOrMuteCall(callerNumber: String?, settings: UserSettings): Boolean {
        val normalized = callerNumber?.trim().orEmpty()
        if (normalized.isEmpty()) return settings.blockUnknownEnabled
        if (settings.whitelistedContacts.any { normalized.contains(it, ignoreCase = true) }) {
            return false
        }
        if (settings.blockedNumbers.any { normalized.contains(it, ignoreCase = true) }) {
            return true
        }
        if (settings.dndEnabled) return true
        val digitsOnly = normalized.filter { it.isDigit() }
        if (settings.blockUnknownEnabled && digitsOnly.length < 7) {
            return true
        }
        return false
    }
}
