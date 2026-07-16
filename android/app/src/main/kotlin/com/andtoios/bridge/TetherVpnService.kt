package com.andtoios.bridge

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class TetherVpnService : VpnService() {

    companion object {
        const val TAG = "TetherVpnService"
        const val VPN_PORT = 8888
        var instance: TetherVpnService? = null
    }

    private var vpnThread: Thread? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "TetherVpnService oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "VPN servisi başlatılıyor")
        if (!isRunning) {
            startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        isRunning = true
        vpnThread = thread {
            try {
                setupVpn()
                startVpnServer()
            } catch (e: Exception) {
                Log.e(TAG, "VPN hatası: ${e.message}", e)
                isRunning = false
            }
        }
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setMtu(1500)
        builder.addAddress("10.8.0.1", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("8.8.8.8")
        builder.addDnsServer("8.8.4.4")
        builder.setSession("AndToIos VPN")

        vpnInterface = builder.establish()
        Log.i(TAG, "VPN interface kuruldu")
    }

    private fun startVpnServer() {
        val serverSocket = ServerSocket(VPN_PORT)
        Log.i(TAG, "VPN server dinleniyor: $VPN_PORT")

        while (isRunning) {
            try {
                val clientSocket = serverSocket.accept()
                thread {
                    handleVpnClient(clientSocket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server accept hatası: ${e.message}")
            }
        }
        serverSocket.close()
    }

    private fun handleVpnClient(socket: Socket) {
        try {
            val input = FileInputStream(vpnInterface?.fileDescriptor)
            val output = FileOutputStream(vpnInterface?.fileDescriptor)

            val clientInput = socket.getInputStream()
            val clientOutput = socket.getOutputStream()

            // iPhone'dan gelen trafiği internete ilet
            thread {
                try {
                    val buffer = ByteArray(32768)
                    while (isRunning && !socket.isClosed) {
                        val len = clientInput.read(buffer)
                        if (len > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "İçeri trafiği hatası: ${e.message}")
                }
            }

            // İnternet trafiğini iPhone'a ilet
            thread {
                try {
                    val buffer = ByteArray(32768)
                    while (isRunning && !socket.isClosed) {
                        val len = input.read(buffer)
                        if (len > 0) {
                            clientOutput.write(buffer, 0, len)
                            clientOutput.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Dışarı trafiği hatası: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handle hatası: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        vpnInterface?.close()
        vpnThread?.interrupt()
        instance = null
        Log.i(TAG, "TetherVpnService durduruldu")
    }
}
