package com.andtoios.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // İçi boş kalabilir, sadece sistemin varlığını görmesi yeterli
    }
}
