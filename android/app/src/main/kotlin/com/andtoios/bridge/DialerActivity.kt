package com.andtoios.bridge

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DialerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri?.scheme == "tel") {
            val number = uri.schemeSpecificPart
            if (!number.isNullOrEmpty()) {
                GsmConnectionService.makeOutgoingCall(this, number)
            }
        }
        finish()
    }
}
