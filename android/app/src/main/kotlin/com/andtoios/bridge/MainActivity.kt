package com.andtoios.bridge

import android.Manifest
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.telecom.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvChecks: TextView
    private lateinit var btnToggle: Button
    private var isRunning = false

    private val permissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.MANAGE_OWN_CALLS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus  = findViewById(R.id.tvStatus)
        tvIp      = findViewById(R.id.tvIp)
        tvChecks  = findViewById(R.id.tvChecks)
        btnToggle = findViewById(R.id.btnToggle)

        btnToggle.setOnClickListener {
            if (isRunning) stopBridge()
            else startSetup()
        }

        runCompatibilityChecks()
    }

    override fun onResume() {
        super.onResume()
        runCompatibilityChecks()
        tvIp.text = "Bu cihazın IP'si: ${BridgeService.instance?.getLocalIp() ?: "Servis kapalı"}"
    }

    // Uyumluluk kontrolleri
    private fun runCompatibilityChecks() {
        val sb = StringBuilder()
        var hasWarning = false

        // Android sürümü
        val sdk = Build.VERSION.SDK_INT
        if (sdk >= 26) sb.append("✅ Android ${Build.VERSION.RELEASE}\n")
        else { sb.append("⚠️ Android ${Build.VERSION.RELEASE} — Android 8+ önerilir\n"); hasWarning = true }

        // Üretici uyarısı
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                sb.append("⚠️ Xiaomi — MIUI arka plan kısıtlamalarını kapatın\n").also { hasWarning = true }
            manufacturer.contains("huawei") ->
                sb.append("⚠️ Huawei — Pil yönetimi kısıtlamalarını kapatın\n").also { hasWarning = true }
            manufacturer.contains("oppo") || manufacturer.contains("vivo") ->
                sb.append("⚠️ ${Build.MANUFACTURER} — Arka plan izinlerini kontrol edin\n").also { hasWarning = true }
            else -> sb.append("✅ ${Build.MANUFACTURER} ${Build.MODEL}\n")
        }

        // Pil optimizasyonu
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName))
            sb.append("✅ Pil optimizasyonu — Muaf\n")
        else {
            sb.append("⚠️ Pil optimizasyonu — Devre dışı bırakın  [Düzelt]\n")
            hasWarning = true
        }

        // Varsayılan telefon uygulaması
        val tm = getSystemService(TelecomManager::class.java)
        if (tm.defaultDialerPackage == packageName)
            sb.append("✅ Varsayılan telefon uygulaması\n")
        else {
            sb.append("⚠️ Varsayılan telefon uygulaması değil  [Düzelt]\n")
            hasWarning = true
        }

        // Varsayılan SMS uygulaması
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms == packageName)
            sb.append("✅ Varsayılan SMS uygulaması\n")
        else {
            sb.append("⚠️ Varsayılan SMS uygulaması değil  [Düzelt]\n")
            hasWarning = true
        }

        // İzinler
        val missingPerms = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isEmpty()) sb.append("✅ Tüm izinler verilmiş\n")
        else { sb.append("⚠️ Eksik izinler: ${missingPerms.size} adet\n"); hasWarning = true }

        tvChecks.text = sb.toString()
        if (!hasWarning) tvChecks.append("\n🎉 Tüm kontroller geçti!")
    }

    private fun startSetup() {
        // 1. İzinler
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
            return
        }

        // 2. Pil optimizasyonu
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Pil Optimizasyonu")
                .setMessage("AndToIos'un arka planda sürekli çalışabilmesi için pil optimizasyonundan muaf tutulması gerekiyor.")
                .setPositiveButton("Ayarları Aç") { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton("Geç", null)
                .show()
            return
        }

        // 3. Varsayılan telefon uygulaması
        val tm = getSystemService(TelecomManager::class.java)
        if (tm.defaultDialerPackage != packageName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java)
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 101)
            } else {
                startActivityForResult(
                    Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName), 101
                )
            }
            return
        }

        // 4. Varsayılan SMS uygulaması
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms != packageName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java)
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 102)
            } else {
                startActivityForResult(
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                    }, 102
                )
            }
            return
        }

        // Hepsi tamam, başlat
        startBridge()
    }

    private fun startBridge() {
        registerPhoneAccount()
        BridgeService.start(this)
        isRunning = true
        btnToggle.text = "Durdur"
        tvStatus.text = "Durum: Çalışıyor ✓"
        runCompatibilityChecks()
    }

    private fun stopBridge() {
        BridgeService.stop(this)
        isRunning = false
        btnToggle.text = "Başlat"
        tvStatus.text = "Durum: Durduruldu"
    }

    private fun registerPhoneAccount() {
        val tm = getSystemService(TelecomManager::class.java)
        val handle = PhoneAccountHandle(
            ComponentName(this, GsmConnectionService::class.java), "AndToIosBridge"
        )
        val account = PhoneAccount.builder(handle, "AndToIos")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        tm.registerPhoneAccount(account)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Her adımdan sonra setup'a devam et
        if (requestCode in 101..102) startSetup()
    }

    override fun onRequestPermissionsResult(requestCode: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, perms, results)
        if (requestCode == 100) startSetup()
    }
}
