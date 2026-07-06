package com.andtoios.bridge

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class BridgeServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        const val TAG = "BridgeServer"
        const val PORT = 8765
    }

    private val gson = Gson()
    var iPhoneSocket: WebSocket? = null
    var onMessageReceived: ((String, JsonObject) -> Unit)? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "iPhone bağlandı: ${conn.remoteSocketAddress}")
        iPhoneSocket = conn
        send("connected", JsonObject().apply {
            addProperty("version", "1.0")
            addProperty("platform", "android")
        })
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.i(TAG, "Bağlantı kapandı: $reason")
        if (iPhoneSocket == conn) iPhoneSocket = null
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            Log.d(TAG, "Gelen mesaj tipi: $type")
            if (type == "ping") { send("pong", JsonObject()); return }
            onMessageReceived?.invoke(type, json)
        } catch (e: Exception) {
            Log.e(TAG, "Mesaj parse hatası: ${e.message}")
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "Hata: ${ex.message}")
    }

    override fun onStart() {
        Log.i(TAG, "BridgeServer başlatıldı port:$PORT")
    }

    fun send(type: String, data: JsonObject) {
        try {
            val socket = iPhoneSocket ?: return
            data.addProperty("type", type)
            socket.send(gson.toJson(data))
        } catch (e: Exception) {
            Log.e(TAG, "Gönderme hatası: ${e.message}")
        }
    }

    fun isIPhoneConnected(): Boolean = iPhoneSocket?.isOpen == true
}
