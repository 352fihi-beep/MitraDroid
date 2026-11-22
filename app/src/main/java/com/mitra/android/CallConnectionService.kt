package com.mitra.android

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.mitra.android.data.local.UserPreferencesRepository
import com.mitra.android.di.ApplicationScope
import com.mitra.android.telephony.PhishingAndSpamAnalyzer
import com.mitra.android.telephony.VoiceSessionController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

object TelecomHelper {
    private const val ACCOUNT_ID = "MitraSelfManagedVoice"

    fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context, CallConnectionService::class.java)
        return PhoneAccountHandle(componentName, ACCOUNT_ID)
    }

    fun ensurePhoneAccountRegistered(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return false
            val handle = getPhoneAccountHandle(context)
            val account = PhoneAccount.builder(handle, "Mitra AI Voice")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build()
            telecomManager.registerPhoneAccount(account)
            true
        } catch (t: Throwable) {
            Log.w("MitraTelecom", "PhoneAccount registration skipped: ${t.message}")
            false
        }
    }

    fun placeSelfManagedCall(context: Context, number: String): Boolean {
        return try {
            ensurePhoneAccountRegistered(context)
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return false
            val handle = getPhoneAccountHandle(context)
            val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            telecomManager.placeCall(uri, extras)
            true
        } catch (t: Throwable) {
            Log.w("MitraTelecom", "Telecom placeCall fallback to in-app session: ${t.message}")
            false
        }
    }
}

@AndroidEntryPoint
class CallConnectionService : ConnectionService() {

    @Inject
    lateinit var voiceSessionController: VoiceSessionController

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var analyzer: PhishingAndSpamAnalyzer

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val number = request?.address?.schemeSpecificPart ?: "Unknown"
        Log.d("MitraTelecom", "Incoming connection created for $number")
        val connection = MitraConnection(voiceSessionController)
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.setCallerDisplayName(number, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        appScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            if (analyzer.shouldBlockOrMuteCall(number, settings)) {
                connection.setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                connection.destroy()
            } else {
                voiceSessionController.attachConnection(connection)
                voiceSessionController.startCallSession(
                    callId = "in-${System.currentTimeMillis()}",
                    contactNumber = number,
                    direction = "INCOMING"
                )
            }
        }
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val number = request?.address?.schemeSpecificPart ?: "+1-787-555-0100"
        Log.d("MitraTelecom", "Outgoing connection created for $number")
        val connection = MitraConnection(voiceSessionController)
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.setDialing()
        connection.setActive()
        voiceSessionController.attachConnection(connection)
        voiceSessionController.startCallSession(
            callId = "out-${System.currentTimeMillis()}",
            contactNumber = number,
            direction = "OUTGOING"
        )
        return connection
    }
}

class MitraConnection(
    private val voiceSessionController: VoiceSessionController? = null
) : Connection() {

    val dtmfHistory: MutableList<Char> = mutableListOf()

    override fun onAnswer(videoState: Int) {
        super.onAnswer(videoState)
        setActive()
        voiceSessionController?.onConnectionStateUpdated("ACTIVE")
        Log.d("MitraTelecom", "Call answered.")
    }

    override fun onHold() {
        super.onHold()
        setOnHold()
        voiceSessionController?.onConnectionStateUpdated("ON_HOLD")
    }

    override fun onUnhold() {
        super.onUnhold()
        setActive()
        voiceSessionController?.onConnectionStateUpdated("ACTIVE")
    }

    override fun onDisconnect() {
        super.onDisconnect()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        voiceSessionController?.onConnectionStateUpdated("DISCONNECTED")
        Log.d("MitraTelecom", "Call disconnected.")
    }

    override fun onAbort() {
        super.onAbort()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
        voiceSessionController?.onConnectionStateUpdated("ABORTED")
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)
        Log.d("MitraTelecom", "Audio route changed: muted=${state?.isMuted}, route=${state?.route}")
    }

    override fun onPlayDtmfTone(c: Char) {
        super.onPlayDtmfTone(c)
        dtmfHistory.add(c)
        Log.d("MitraTelecom", "DTMF Tone: $c")
    }
}
