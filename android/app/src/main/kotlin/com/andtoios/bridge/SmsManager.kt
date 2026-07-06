package com.andtoios.bridge

import android.content.Context
import android.util.Log

object SmsManager {
    fun sendSms(context: Context, to: String, body: String) {
        try {
            val manager = android.telephony.SmsManager.getDefault()
            val parts   = manager.divideMessage(body)
            if (parts.size == 1) {
                manager.sendTextMessage(to, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(to, null, parts, null, null)
            }
            Log.i("SmsManager", "SMS gönderildi: $to")
            BridgeService.instance?.server?.send("sms_sent", com.google.gson.JsonObject().apply {
                addProperty("to", to)
                addProperty("success", true)
            })
        } catch (e: Exception) {
            Log.e("SmsManager", "SMS gönderilemedi: ${e.message}")
            BridgeService.instance?.server?.send("sms_sent", com.google.gson.JsonObject().apply {
                addProperty("to", to)
                addProperty("success", false)
                addProperty("error", e.message)
            })
        }
    }
}
