package com.andtoios.bridge

import android.Manifest
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.provider.Telephony
import android.telecom.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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

    // Zincirleme açılış izin istekleri için Activity Sonuç Takipçileri
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            checkAndRequestBatteryOptimization()
        } else {
            Toast.makeText(this, "Uygulamanın çalışması için izinleri vermelisiniz.", Toast.LENGTH_LONG).show()
        }
    }

    private val batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkAndRequestDefaultDialer()
    }

    private val dialerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkAndRequestDefaultSms()
    }

    private val smsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        runCompatibilityChecks()
        Toast.makeText(this, "Tüm kurulum başarıyla tamamlandı!", Toast.LENGTH_SHORT).show()
    }

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

        // UYGULAMA AÇILDIĞINDA OTOMATİK İZİN SÜRECİNİ BAŞLAT
        startAutomaticSetupChain()
    }

    override fun onResume() {
        super.onResume()
        runCompatibilityChecks()
        tvIp.text = "Bu cihazın IP'si: ${BridgeService.instance?.getLocalIp() ?: "Servis kapalı"}"
    }

    // Açılışta otomatik çalışan zincirleme kontrol fonksiyonu
    private fun startAutomaticSetupChain() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            checkAndRequestBatteryOptimization()
        }
    }

    private fun checkAndRequestBatteryOptimization() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Pil Optimizasyonu")
                .setMessage("AndToIos'un arka planda sürekli çalışabilmesi için pil optimizasyonundan muaf tutulması gerekiyor.")
                .setCancelable(false)
                .setPositiveButton("Ayarları Aç") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    batteryLauncher.launch(intent)
                }
                .setNegativeButton("Geç") { _, _ -> checkAndRequestDefaultDialer() }
                .show()
        } else {
            checkAndRequestDefaultDialer()
        }
    }

    private fun checkAndRequestDefaultDialer() {
        val tm = getSystemService(TelecomManager::class.java)
        if (tm.defaultDialerPackage != packageName) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            dialerLauncher.launch(intent)
        } else {
            checkAndRequestDefaultSms()
        }
    }

    private fun checkAndRequestDefaultSms() {
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms != packageName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                    val intent = rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    smsLauncher.launch(intent)
                }
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
                smsLauncher.launch(intent)
            }
        } else {
            runCompatibilityChecks()
        }
    }

    private fun runCompatibilityChecks() {
        val sb = StringBuilder()
        var hasWarning = false

        val sdk = Build.VERSION.SDK_INT
        if (sdk >= 26) sb.append("✅ Android ${Build.VERSION.RELEASE}\n")
        else { sb.append("⚠️ Android ${Build.VERSION.RELEASE} — Android 8+ önerilir\n"); hasWarning = true }

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

        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName))
            sb.append("✅ Pil optimizasyonu — Muaf\n")
        else {
            sb.append("⚠️ Pil optimizasyonu — Devre dışı bırakın\n")
            hasWarning = true
        }

        val tm = getSystemService(TelecomManager::class.java)
        if (tm.defaultDialerPackage == packageName)
            sb.append("✅ Varsayılan telefon uygulaması\n")
        else {
            sb.append("⚠️ Varsayılan telefon uygulaması değil\n")
            hasWarning = true
        }

        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms == packageName)
            sb.append("✅ Varsayılan SMS uygulaması\n")
        else {
            sb.append("⚠️ Varsayılan SMS uygulaması değil\n")
            hasWarning = true
        }

        val missingPerms = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isEmpty()) sb.append("✅ Tüm izinler verilmiş\n")
        else { sb.append("⚠️ Eksik izinler: ${missingPerms.size} adet\n"); hasWarning = true }

        tvChecks.text = sb.toString()
        if (!hasWarning) tvChecks.append("\n🎉 Tüm kontroller geçti!")
    }

    private fun startSetup() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
            return
        }

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

        val tm = getSystemService(TelecomManager::class.java)
        if (tm.defaultDialerPackage != packageName) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            startActivityForResult(intent, 101)
            return
        }

        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms != packageName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java)
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 102)
            } else {
