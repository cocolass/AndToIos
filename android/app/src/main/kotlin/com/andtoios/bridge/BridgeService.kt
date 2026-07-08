package com.andtoios.bridge

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import java.net.NetworkInterface

class BridgeService : Service() {

    companion object {
        const val TAG = "BridgeService"
        const val CHANNEL_ID = "bridge_channel"
        const val NOTIF_ID = 1
        var instance: BridgeService? = null

        fun start(context: Context) =
            context.startForegroundService(Intent(context, BridgeService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, BridgeService::class.java))
    }

    lateinit var server: BridgeServer
    private lateinit var webRtcManager: WebRtcManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Başlatılıyor..."))
        initServer()
    }

    private fun initServer() {
        server = BridgeServer(BridgeServer.PORT)
        webRtcManager = WebRtcManager(this, server)

        server.onMessageReceived = { type, data ->
            handleMessage(type, data)
        }

        server.start()
        val ip = getLocalIp()
        updateNotification("Hazır — iPhone bağlantısı bekleniyor\nIP: $ip")
        Log.i(TAG, "Bridge servisi başlatıldı, IP: $ip")
    }

    private fun handleMessage(type: String, data: JsonObject) {
        Log.i(TAG, "Mesaj işleniyor: $type")
        when (type) {
            "webrtc_answer" -> webRtcManager.handleAnswer(data)
            "webrtc_ice"    -> webRtcManager.handleIceCandidate(data)
            "call_answer"   -> {
                Log.i(TAG, "iPhone aramayı cevapladı")
                GsmConnectionService.answerActiveCall()
            }
            "call_reject"   -> GsmConnectionService.rejectActiveCall()
            "call_end"      -> GsmConnectionService.endActiveCall()
            "call_outgoing" -> {
                val number = data.get("number")?.asString ?: return
                GsmConnectionService.makeOutgoingCall(this, number)
            }
            "sms_send" -> {
                val to   = data.get("to")?.asString ?: return
                val body = data.get("body")?.asString ?: return
                SmsManager.sendSms(this, to, body)
            }
            else -> Log.w(TAG, "Bilinmeyen mesaj tipi: $type")
        }
    }

    fun notifyIncomingCall(caller: String) {
        Log.i(TAG, "notifyIncomingCall çağrıldı: $caller, iPhone bağlı mı: ${server.isIPhoneConnected()}")
        webRtcManager.createOffer { offer ->
            Log.i(TAG, "WebRTC offer oluşturuldu, iPhone'a gönderiliyor")
            server.send("call_incoming", JsonObject().apply {
                addProperty("caller", caller)
                addProperty("sdp", offer)
            })
        }
        updateNotification("Gelen arama: $caller")
    }

    fun notifyIncomingSms(sender: String, body: String, timestamp: Long) {
        server.send("sms_incoming", JsonObject().apply {
            addProperty("sender", sender)
            addProperty("body", body)
            addProperty("timestamp", timestamp)
        })
        Log.i(TAG, "SMS iletildi: $sender")
    }

    fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidates = mutableListOf<Pair<String, String>>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                if (!iface.isUp || iface.isLoopback) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val ip = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && !ip.contains(":")) {
                        Log.i(TAG, "Interface bulundu: $name -> $ip")
                        candidates.add(name to ip)
                    }
                }
            }

            // Öncelik: wlan, ap, softap gibi hotspot interface isimleri
            val hotspotIface = candidates.firstOrNull {
                it.first.contains("wlan") || it.first.contains("ap") || it.first.contains("softap")
            }
            if (hotspotIface != null) return hotspotIface.second

            // Mobil veri interface'lerini (rmnet, seth_lte, ccmni) hariç tut
            val nonMobile = candidates.firstOrNull {
                !it.first.contains("rmnet") && !it.first.contains("lte") && !it.first.contains("ccmni")
            }
            if (nonMobile != null) return nonMobile.second

            return candidates.firstOrNull()?.second ?: "IP alınamadı"
        } catch (e: Exception) {
            Log.e(TAG, "IP alma hatası: ${e.message}")
        }
        return "IP alınamadı"
    }

    fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "AndToIos Bridge", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "GSM-iPhone köprü servisi" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AndToIos")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::server.isInitialized) server.stop()
        if (::webRtcManager.isInitialized) webRtcManager.dispose()
    }
}
