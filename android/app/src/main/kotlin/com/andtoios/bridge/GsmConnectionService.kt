package com.andtoios.bridge

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.*
import android.util.Log

// Not: Gelen aramalar artık CallInCallService üzerinden yakalanıyor.
// Bu sınıf sadece giden arama başlatmak için kullanılıyor.
class GsmConnectionService : ConnectionService() {

    companion object {
        const val TAG = "GsmConnectionService"

        fun makeOutgoingCall(context: Context, number: String) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
        }

        fun answerActiveCall() {
            CallInCallService.activeCall?.answer(0)
                ?: Log.w(TAG, "Aktif çağrı yok, cevaplanamadı")
        }

        fun rejectActiveCall() {
            CallInCallService.activeCall?.reject(false, null)
                ?: Log.w(TAG, "Aktif çağrı yok, reddedilemedi")
        }

        fun endActiveCall() {
            CallInCallService.activeCall?.disconnect()
                ?: Log.w(TAG, "Aktif çağrı yok, kapatılamadı")
        }
    }

    override fun onCreateOutgoingConnection(
        mgr: PhoneAccountHandle?, request: ConnectionRequest
    ): Connection {
        val number = request.address?.schemeSpecificPart ?: ""
        Log.i(TAG, "Giden GSM çağrısı: $number")
        val conn = GsmConnection(number)
        conn.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        conn.setDialing()
        return conn
    }

    inner class GsmConnection(private val number: String) : Connection() {
        init { audioModeIsVoip = false }

        override fun onAnswer() {
            super.onAnswer(); setActive()
        }

        override fun onDisconnect() {
            super.onDisconnect()
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }
    }
}
