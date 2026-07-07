package com.andtoios.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Sistem bu kodu canlı görsün diye log atıyoruz
        Log.d("AndToIosSMS", "SMS Alındı: ${intent?.action}")
    }
}
