package com.andtoios.bridge

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
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
        startAutomaticSetupChain()
    }

    override fun onResume() {
        super.onResume()
        runCompatibilityChecks()
        try {
            tvIp.text = "Cihaz IP: ${BridgeService.instance?.getLocalIp() ?: "Servis pasif"}"
        } catch (e: Exception) {
            tvIp.text = "Cihaz IP: Alınamadı"
        }
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

        val hasSmsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (hasSmsPermission) {
            sb.append("✅ SMS Okuma ve Yakalama İzni Aktif\n")
        } else {
            sb.append("⚠️ SMS İzni Eksik\n")
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
            // Otomatik başlatmayı çökme riskine karşı kontrollü yapıyoruz
            if (!isRunning) {
                try {
                    startBridge()
                } catch (e: Exception) {
                    tvChecks.append("\n❌ Servis başlatılamadı: ${e.message}")
                }
            }
        }
    }

    private fun startSetup() {
        startAutomaticSetupChain()
    }

    private fun startBridge() {
        try {
            val serviceIntent = Intent(this, BridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isRunning = true
            tvStatus.text = "Durum: Köprü Aktif"
            btnToggle.text = "Köprüyü Durdur"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Servis başlatılırken hata oluştu!", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopBridge() {
        isRunning = false
        tvStatus.text = "Durum: Pasif"
        btnToggle.text = "Köprüyü Başlat"
        try {
            stopService(Intent(this, BridgeService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
