package com.andtoios.bridge

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_incoming_call)
        val caller = intent.getStringExtra("caller") ?: "Bilinmiyor"
        findViewById<TextView>(R.id.tvCaller).text = caller
        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            GsmConnectionService.answerActiveCall(); finish()
        }
        findViewById<Button>(R.id.btnReject).setOnClickListener {
            GsmConnectionService.rejectActiveCall(); finish()
        }
    }
}
