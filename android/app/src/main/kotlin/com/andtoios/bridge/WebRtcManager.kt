package com.andtoios.bridge

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import io.getstream.webrtc.android.core.*
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val server: BridgeServer
) {

    companion object {
        const val TAG = "WebRtcManager"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        initFactory()
    }

    private fun initFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        Log.i(TAG, "PeerConnectionFactory hazır")
    }

    private fun createPeerConnection(): PeerConnection? {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        return peerConnectionFactory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                server.send("webrtc_ice", JsonObject().apply {
                    addProperty("sdpMid", candidate.sdpMid)
                    addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
                    addProperty("candidate", candidate.sdp)
                })
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.i(TAG, "Bağlantı durumu: $state")
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED ->
                        BridgeService.instance?.updateNotification("Ses bağlantısı kuruldu ✓")
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    PeerConnection.PeerConnectionState.FAILED ->
                        BridgeService.instance?.updateNotification("Ses bağlantısı kesildi")
                    else -> {}
                }
            }

            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                Log.i(TAG, "Uzak ses track alındı")
            }
        })
    }

    fun createOffer(callback: (String) -> Unit) {
        peerConnection?.close()

        val pc = createPeerConnection() ?: return
        peerConnection = pc

        // Ses track ekle
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio0", audioSource)
        pc.addTrack(localAudioTrack, listOf("stream0"))

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { callback(sdp.description) }
                    override fun onSetFailure(error: String?) = Log.e(TAG, "SetLocal hatası: $error")
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String?) = Log.e(TAG, "Offer hatası: $error")
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun handleAnswer(data: JsonObject) {
        val sdp = data.get("sdp")?.asString ?: return
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() = Log.i(TAG, "Remote answer set edildi")
            override fun onSetFailure(error: String?) = Log.e(TAG, "SetRemote hatası: $error")
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, answer)
    }

    fun handleIceCandidate(data: JsonObject) {
        val candidate = IceCandidate(
            data.get("sdpMid")?.asString ?: "",
            data.get("sdpMLineIndex")?.asInt ?: 0,
            data.get("candidate")?.asString ?: ""
        )
        peerConnection?.addIceCandidate(candidate)
    }

    fun endCall() {
        peerConnection?.close()
        peerConnection = null
        localAudioTrack?.dispose()
        localAudioTrack = null
    }

    fun dispose() {
        endCall()
        peerConnectionFactory?.dispose()
    }
}
