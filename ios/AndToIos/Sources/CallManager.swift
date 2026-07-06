import Foundation
import CallKit
import AVFoundation
import WebRTC

class CallManager: NSObject, ObservableObject {

    static let shared = CallManager()

    private let provider: CXProvider
    private let callController = CXCallController()
    private var activeCalls: [UUID: String] = [:] // uuid → callerNumber
    private var peerConnection: RTCPeerConnection?
    private var factory: RTCPeerConnectionFactory?
    private var localAudioTrack: RTCAudioTrack?

    private static let providerConfig: CXProviderConfiguration = {
        let c = CXProviderConfiguration()
        c.supportsVideo = false
        c.maximumCallsPerCallGroup = 1
        c.supportedHandleTypes = [.phoneNumber]
        c.includesCallsInRecents = true
        return c
    }()

    override init() {
        provider = CXProvider(configuration: Self.providerConfig)
        super.init()
        provider.setDelegate(self, queue: .main)
        initWebRTC()
        setupBridgeMessages()
    }

    // MARK: - WebRTC

    private func initWebRTC() {
        RTCInitializeSSL()
        let encoderFactory = RTCDefaultVideoEncoderFactory()
        let decoderFactory = RTCDefaultVideoDecoderFactory()
        factory = RTCPeerConnectionFactory(
            encoderFactory: encoderFactory,
            decoderFactory: decoderFactory
        )
    }

    private func createPeerConnection(sdpOffer: String, completion: @escaping (String?) -> Void) {
        let config = RTCConfiguration()
        config.iceServers = [RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])]
        config.sdpSemantics = .unifiedPlan
        config.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveAudio": "true"],
            optionalConstraints: nil
        )

        guard let pc = factory?.peerConnection(with: config, constraints: constraints, delegate: self) else {
            completion(nil); return
        }

        peerConnection = pc

        // Ses track ekle
        let audioConstraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        let audioSource = factory?.audioSource(with: audioConstraints)
        localAudioTrack = factory?.audioTrack(with: audioSource!, trackId: "audio0")
        pc.add(localAudioTrack!, streamIds: ["stream0"])

        // Remote offer'ı set et
        let offer = RTCSessionDescription(type: .offer, sdp: sdpOffer)
        pc.setRemoteDescription(offer) { error in
            if let error = error { print("SetRemote hatası: \(error)"); completion(nil); return }

            // Answer oluştur
            pc.answer(for: constraints) { answer, error in
                guard let answer = answer else { completion(nil); return }
                pc.setLocalDescription(answer) { error in
                    if error == nil { completion(answer.sdp) }
                    else { completion(nil) }
                }
            }
        }
    }

    // MARK: - Gelen arama (Android'den)

    func handleIncomingCall(caller: String, sdpOffer: String) {
        let uuid = UUID()
        activeCalls[uuid] = caller

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: caller)
        update.localizedCallerName = caller
        update.hasVideo = false
        update.supportsHolding = true
        update.supportsMuting = true

        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if let error = error {
                print("CallKit gelen arama hatası: \(error)")
                self?.activeCalls.removeValue(forKey: uuid)
            } else {
                // WebRTC answer hazırla
                self?.createPeerConnection(sdpOffer: sdpOffer) { answer in
                    guard let answer = answer else { return }
                    BridgeClient.shared.send(type: "webrtc_answer", data: ["sdp": answer])
                }
            }
        }
    }

    // MARK: - Giden arama (iPhone'dan)

    func makeCall(number: String) {
        let uuid = UUID()
        activeCalls[uuid] = number
        let handle = CXHandle(type: .phoneNumber, value: number)
        let action = CXStartCallAction(call: uuid, handle: handle)
        action.isVideo = false
        callController.request(CXTransaction(action: action)) { error in
            if let error = error { print("Giden arama hatası: \(error)") }
        }
    }

    func endAllCalls() {
        for (uuid, _) in activeCalls {
            provider.reportCall(with: uuid, endedAt: Date(), reason: .remoteEnded)
        }
        activeCalls.removeAll()
        peerConnection?.close()
        peerConnection = nil
    }

    // MARK: - Bridge mesajları

    private func setupBridgeMessages() {
        BridgeClient.shared.onMessage = { [weak self] type, data in
            switch type {
            case "call_incoming":
                let caller = data["caller"] as? String ?? "Bilinmiyor"
                let sdp    = data["sdp"]    as? String ?? ""
                self?.handleIncomingCall(caller: caller, sdpOffer: sdp)

            case "call_ended":
                self?.endAllCalls()

            case "webrtc_ice":
                if let sdpMid   = data["sdpMid"] as? String,
                   let sdpIndex = data["sdpMLineIndex"] as? Int32,
                   let candidate = data["candidate"] as? String {
                    let ice = RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpIndex, sdpMid: sdpMid)
                    self?.peerConnection?.add(ice)
                }

            default: break
            }
        }
    }

    private func configureAudio() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth])
        try? session.setActive(true)
    }
}

// MARK: - CXProviderDelegate

extension CallManager: CXProviderDelegate {

    func providerDidReset(_ provider: CXProvider) {
        peerConnection?.close()
        activeCalls.removeAll()
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        configureAudio()
        BridgeClient.shared.send(type: "call_answer", data: [:])
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        BridgeClient.shared.send(type: "call_end", data: [:])
        activeCalls.removeValue(forKey: action.callUUID)
        peerConnection?.close()
        peerConnection = nil
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        let number = activeCalls[action.callUUID] ?? ""
        BridgeClient.shared.send(type: "call_outgoing", data: ["number": number])
        configureAudio()
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        localAudioTrack?.isEnabled = !action.isMuted
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        localAudioTrack?.isEnabled = !action.isOnHold
        action.fulfill()
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        RTCAudioSession.sharedInstance().audioSessionDidActivate(audioSession)
        RTCAudioSession.sharedInstance().isAudioEnabled = true
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        RTCAudioSession.sharedInstance().audioSessionDidDeactivate(audioSession)
        RTCAudioSession.sharedInstance().isAudioEnabled = false
    }
}

// MARK: - RTCPeerConnectionDelegate

extension CallManager: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange state: RTCIceConnectionState) {
        print("ICE: \(state.rawValue)")
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        BridgeClient.shared.send(type: "webrtc_ice", data: [
            "sdpMid": candidate.sdpMid ?? "",
            "sdpMLineIndex": candidate.sdpMLineIndex,
            "candidate": candidate.sdp
        ])
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
