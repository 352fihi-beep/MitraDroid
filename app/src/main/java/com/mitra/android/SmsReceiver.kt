package com.mitra.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.data.repository.MitraRepository
import com.mitra.android.di.ApplicationScope
import com.mitra.android.telephony.PhishingAndSpamAnalyzer
import com.mitra.android.telephony.SmsDispatcher
import com.mitra.android.telephony.VoiceSessionController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsReceiverEntryPoint {
    fun repository(): MitraRepository
    fun preferencesRepository(): UserPreferencesRepository
    fun aiConversationManager(): AIConversationManager
    fun phishingAnalyzer(): PhishingAndSpamAnalyzer
    fun smsDispatcher(): SmsDispatcher
    fun voiceSessionController(): VoiceSessionController
    @ApplicationScope
    fun appScope(): CoroutineScope
}

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull().orEmpty()

        val groupedBodies = mutableMapOf<String, StringBuilder>()
        if (messages.isNotEmpty()) {
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "Unknown"
                val body = sms.messageBody ?: ""
                groupedBodies.getOrPut(sender) { StringBuilder() }.append(body)
            }
        } else {
            val fallbackSender = intent.getStringExtra("extra_sender")
            val fallbackBody = intent.getStringExtra("extra_body")
            if (!fallbackSender.isNullOrBlank() && !fallbackBody.isNullOrBlank()) {
                groupedBodies[fallbackSender] = StringBuilder(fallbackBody)
            }
        }

        if (groupedBodies.isEmpty()) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsReceiverEntryPoint::class.java
        )
        val pendingResult = goAsync()

        entryPoint.appScope().launch {
            try {
                val settings = entryPoint.preferencesRepository().getCurrentSettings()
                val analyzer = entryPoint.phishingAnalyzer()
                val repository = entryPoint.repository()
                val aiManager = entryPoint.aiConversationManager()
                val dispatcher = entryPoint.smsDispatcher()
                val voice = entryPoint.voiceSessionController()

                for ((sender, bodyBuilder) in groupedBodies) {
                    val fullBody = bodyBuilder.toString()
                    val assessment = analyzer.analyzeSms(fullBody)
                    val flagged = settings.phishingGuardEnabled && assessment.isSuspicious

                    repository.recordSms(
                        contactId = sender,
                        message = fullBody,
                        isFromAi = false,
                        isPhishingFlagged = flagged,
                        deliveryStatus = "RECEIVED"
                    )

                    if (settings.readAloudSmsEnabled && !flagged) {
                        voice.speakText("Mensaje de $sender: $fullBody")
                    }

                    if (settings.autoReplySmsEnabled && !flagged && !settings.dndEnabled) {
                        val reply = aiManager.generateReply(
                            prompt = fullBody,
                            contact = sender,
                            isCall = false
                        )
                        dispatcher.sendSms(sender, reply.text, isFromAi = true)
                    }
                    Log.d("MitraTelecom", "Processed SMS from $sender (flagged=$flagged)")
                }
            } catch (t: Throwable) {
                Log.e("MitraTelecom", "Error handling incoming SMS", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
