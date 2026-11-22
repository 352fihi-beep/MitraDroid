package com.mitra.android.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.mitra.android.data.repository.MitraRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MitraRepository
) {
    fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun sendSms(
        recipient: String,
        body: String,
        isFromAi: Boolean = false
    ): Result<String> {
        val trimmedRecipient = recipient.trim()
        val trimmedBody = body.trim()
        if (trimmedRecipient.isEmpty() || trimmedBody.isEmpty()) {
            return Result.failure(IllegalArgumentException("Recipient and message cannot be empty."))
        }

        if (!hasSendSmsPermission()) {
            repository.recordSms(
                contactId = trimmedRecipient,
                message = trimmedBody,
                isFromAi = isFromAi,
                deliveryStatus = "LOCAL_ONLY_NO_PERMISSION"
            )
            return Result.success("Saved locally (grant SEND_SMS permission to dispatch over carrier)")
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(trimmedBody)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(trimmedRecipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(trimmedRecipient, null, trimmedBody, null, null)
            }
            repository.recordSms(
                contactId = trimmedRecipient,
                message = trimmedBody,
                isFromAi = isFromAi,
                deliveryStatus = "SENT"
            )
            Result.success("Dispatched via SMS")
        } catch (t: Throwable) {
            repository.recordSms(
                contactId = trimmedRecipient,
                message = trimmedBody,
                isFromAi = isFromAi,
                deliveryStatus = "FAILED"
            )
            Result.failure(t)
        }
    }
}
