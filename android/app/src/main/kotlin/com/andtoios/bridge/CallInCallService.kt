package com.andtoios.bridge

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class CallInCallService : InCallService() {

    companion object {
        const val TAG = "CallInCallService"
        var activeCall: Call? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "Çağrı eklendi: ${call.details.handle}")
        activeCall = call

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.i(TAG, "Çağrı durumu değişti: $state")
            }
        }
        call.registerCallback(callback)

        when (call.state) {
            Call.STATE_RINGING -> {
                val number = call.details.handle?.schemeSpecificPart ?: "Bilinmiyor"
                Log.i(TAG, "Gelen arama (RINGING): $number")
                BridgeService.instance?.notifyIncomingCall(number)
            }
            else -> {
                Log.i(TAG, "Çağrı durumu: ${call.state}")
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "Çağrı kaldırıldı")
        activeCall = null
        BridgeService.instance?.server?.send("call_ended", com.google.gson.JsonObject())
    }

    fun answerCall() {
        activeCall?.answer(0)
    }

    fun rejectCall() {
        activeCall?.reject(false, null)
    }

    fun endCall() {
        activeCall?.disconnect()
    }
}
