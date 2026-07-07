package com.andtoios.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "android.provider.Telephony.SMS_RECEIVED") {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress // Gönderen numara
                val body = sms.displayMessageBody // Mesaj içeriği
                
                Log.d("AndToIosSMS", "Köprü Mesajı Yakaladı -> Kimden: $sender, Mesaj: $body")
                
                // iOS'e veya sunucuya gönderme kodunu doğrudan buraya bağlayabilirsin.
            }
        }
    }
}
