package com.andtoios.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        Log.d("AndToIosSMS", "SMS Servis Bağlandı")
        return null
    }
}
