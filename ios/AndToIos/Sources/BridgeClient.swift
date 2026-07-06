import Foundation
import Combine

/**
 * Android'deki BridgeServer'a bağlanan WebSocket istemcisi.
 * Tüm mesajlaşma bu sınıf üzerinden geçer.
 */
class BridgeClient: NSObject, ObservableObject {

    static let shared = BridgeClient()

    @Published var isConnected = false
    @Published var androidIP: String = UserDefaults.standard.string(forKey: "android_ip") ?? ""

    private var webSocketTask: URLSessionWebSocketTask?
    private var pingTimer: Timer?
    private var reconnectTimer: Timer?
    private let session = URLSession(configuration: .default)

    var onMessage: ((String, [String: Any]) -> Void)?

    func connect(ip: String) {
        androidIP = ip
        UserDefaults.standard.set(ip, forKey: "android_ip")

        guard let url = URL(string: "ws://\(ip):8765") else { return }

        disconnect()
        webSocketTask = session.webSocketTask(with: url)
        webSocketTask?.resume()
        receiveMessages()

        // Ping her 30 saniyede
        pingTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            self?.send(type: "ping", data: [:])
        }

        print("BridgeClient: Bağlanıyor → \(ip)")
    }

    func disconnect() {
        pingTimer?.invalidate()
        reconnectTimer?.invalidate()
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        DispatchQueue.main.async { self.isConnected = false }
    }

    func send(type: String, data: [String: Any]) {
        var payload = data
        payload["type"] = type
        guard let jsonData = try? JSONSerialization.data(withJSONObject: payload),
              let jsonStr = String(data: jsonData, encoding: .utf8) else { return }

        webSocketTask?.send(.string(jsonStr)) { error in
            if let error = error {
                print("BridgeClient gönderme hatası: \(error)")
            }
        }
    }

    private func receiveMessages() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                if case .string(let text) = message {
                    self.handleRawMessage(text)
                }
                self.receiveMessages() // Dinlemeye devam et

            case .failure(let error):
                print("BridgeClient alım hatası: \(error)")
                DispatchQueue.main.async { self.isConnected = false }
                self.scheduleReconnect()
            }
        }
    }

    private func handleRawMessage(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return }

        DispatchQueue.main.async {
            if type == "connected" {
                self.isConnected = true
                print("BridgeClient: Android'e bağlandı ✓")
            } else if type == "pong" {
                // Canlılık onayı
            } else {
                self.onMessage?(type, json)
            }
        }
    }

    private func scheduleReconnect() {
        guard !androidIP.isEmpty else { return }
        reconnectTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: false) { [weak self] _ in
            guard let self = self else { return }
            print("BridgeClient: Yeniden bağlanıyor...")
            self.connect(ip: self.androidIP)
        }
    }
}
