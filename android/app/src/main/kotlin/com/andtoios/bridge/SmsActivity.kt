package com.andtoios.bridge

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Varsayılan SMS uygulaması olmak için gerekli
class SmsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
