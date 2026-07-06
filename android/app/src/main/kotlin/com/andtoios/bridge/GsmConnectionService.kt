package com.andtoios.bridge

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.*
import android.util.Log

class GsmConnectionService : ConnectionService() {

    companion object {
        const val TAG = "GsmConnectionService"
        private var activeConnection: GsmConnection? = null

        fun makeOutgoingCall(context: Context, number: String) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
        }

        fun answerActiveCall() { activeConnection?.setActive() }
        fun rejectActiveCall() { activeConnection?.onReject() }
        fun endActiveCall()    { activeConnection?.onDisconnect() }
    }

    override fun onCreateIncomingConnection(
        mgr: PhoneAccountHandle?, request: ConnectionRequest
    ): Connection {
        val number = request.address?.schemeSpecificPart ?: "Bilinmiyor"
        Log.i(TAG, "Gelen GSM: $number")

        val conn = GsmConnection(number)
        activeConnection = conn
        conn.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        conn.setCallerDisplayName(number, TelecomManager.PRESENTATION_ALLOWED)
        conn.connectionCapabilities = Connection.CAPABILITY_MUTE or Connection.CAPABILITY_SUPPORT_HOLD

        // iPhone'a bildir
        BridgeService.instance?.notifyIncomingCall(number)

        return conn
    }

    override fun onCreateOutgoingConnection(
        mgr: PhoneAccountHandle?, request: ConnectionRequest
    ): Connection {
        val number = request.address?.schemeSpecificPart ?: ""
        val conn = GsmConnection(number)
        activeConnection = conn
        conn.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        conn.setDialing()
        return conn
    }

    inner class GsmConnection(private val number: String) : Connection() {
        init { audioModeIsVoip = false }

        override fun onAnswer() {
            super.onAnswer(); setActive()
            Log.i(TAG, "Cevaplandı: $number")
        }

        override fun onReject() {
            super.onReject()
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy(); activeConnection = null
            BridgeService.instance?.server?.send("call_ended", com.google.gson.JsonObject())
        }

        override fun onDisconnect() {
            super.onDisconnect()
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy(); activeConnection = null
            BridgeService.instance?.server?.send("call_ended", com.google.gson.JsonObject())
            BridgeService.instance?.updateNotification("Hazır")
        }

        override fun onHold()   { super.onHold();   setOnHold() }
        override fun onUnhold() { super.onUnhold(); setActive() }
    }
}
