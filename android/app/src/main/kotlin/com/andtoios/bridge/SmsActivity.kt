package com.andtoios.bridge

import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SmsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Basit bir arayüz (Hata vermemesi için)
        val textView = TextView(this).apply {
            text = "AndToIos SMS Köprüsü Aktif"
            textSize = 18f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)

        // ANDROID İÇİN KRİTİK KONTROL: 
        // Eğer başka bir uygulama dışarıdan SMS göndermek için bu aktiviteyi tetiklerse
        // Android bu intent'i yakalayıp yakalayamadığını kontrol eder.
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        
        // Dışarıdan (örneğin rehberden) birine SMS gönder denildiğinde 
        // uygulamanın çökmesini engelleyen ve sistemi ikna eden yapı
        if (Intent.ACTION_SEND == intent.action || Intent.ACTION_SENDTO == intent.action) {
            val uri = intent.data
            val scheme = uri?.scheme
            if ("sms" == scheme || "smsto" == scheme || "mms" == scheme || "mmsto" == scheme) {
                // İleride dışarıdan gelen SMS gönderme isteklerini burada işleyebilirsin.
            }
        }
    }
}
