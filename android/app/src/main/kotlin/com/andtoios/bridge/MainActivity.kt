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
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvChecks: TextView
    private lateinit var btnToggle: Button
    private var isRunning = false

    // Android sürümlerine göre dinamik tehlikeli izin listesi
    private val permissions: Array<String>
        get() {
            val baseList = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.MANAGE_OWN_CALLS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                baseList.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return baseList.toTypedArray()
        }

    // Zincirleme otomasyon izin süreçleri için modern Result API kontratları
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            checkAndRequestBatteryOptimization()
        } else {
            Toast.makeText(this, "Köprünün çalışması için temel izinleri onaylamalısınız.", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "Kurulum başarıyla tamamlandı!", Toast.LENGTH_SHORT).show()
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
        
        // Uygulama açılışında doğrusal zinciri otomatik tetikle
        startAutomaticSetupChain()
    }

    override fun onResume() {
        super.onResume()
        runCompatibilityChecks()
        // Servis ayaktaysa dinamik IP bilgisini arayüze bas
        tvIp.text = "Cihaz IP: ${BridgeService.instance?.getLocalIp() ?: "Servis pasif"}"
    }

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
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Pil Muafiyeti")
                .setMessage("AndToIos sisteminin arka planda kesintisiz veri aktarabilmesi için pil optimizasyonundan çıkarılması gerekir.")
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
        if (tm != null && tm.defaultDialerPackage != packageName) {
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
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
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
                sb.append("⚠️ Huawei — Pil kısıtlamalarını elinizle kapatın\n").also { hasWarning = true }
            manufacturer.contains("oppo") || manufacturer.contains("vivo") ->
                sb.append("⚠️ ${Build.MANUFACTURER} — Arka plan izinlerini denetleyin\n").also { hasWarning = true }
            else -> sb.append("✅ ${Build.MANUFACTURER} ${Build.MODEL}\n")
        }

        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
            sb.append("✅ Pil optimizasyonu — Muaf\n")
        } else {
            sb.append("⚠️ Pil optimizasyonu — Muafiyet eksik\n")
            hasWarning = true
        }

        val tm = getSystemService(TelecomManager::class.java)
        if (tm != null && tm.defaultDialerPackage == packageName) {
            sb.append("✅ Varsayılan telefon uygulaması\n")
        } else {
            sb.append("⚠️ Varsayılan telefon uygulaması seçilmedi\n")
            hasWarning = true
        }

        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms == packageName) {
            sb.append("✅ Varsayılan SMS uygulaması\n")
        } else {
            sb.append("⚠️ Varsayılan SMS uygulaması seçilmedi\n")
            hasWarning = true
        }

        val missingPerms = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isEmpty()) sb.append("✅ Tüm çalışma izinleri tamam\n")
        else { sb.append("⚠️ Eksik izinler var (${missingPerms.size} adet)\n"); hasWarning = true }

        tvChecks.text = sb.toString()
        if (!hasWarning) {
            tvChecks.append("\n🎉 Tüm sistem entegrasyonu doğrulandı!")
            if (!isRunning) startBridge()
        }
    }

    private fun startSetup() {
        startAutomaticSetupChain()
    }

    private fun startBridge() {
        isRunning = true
        tvStatus.text = "Durum: Köprü Aktif"
        btnToggle.text = "Köprüyü Durdur"
        try {
            val serviceIntent = Intent(this, BridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopBridge() {
        isRunning = false
        tvStatus.text = "Durum: Pasif"
        btnToggle.text = "Köprüyü Başlat"
        stopService(Intent(this, BridgeService::class.java))
    }
}
