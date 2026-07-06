import Foundation
import UserNotifications

class SmsManager: ObservableObject {

    static let shared = SmsManager()

    @Published var messages: [SmsMessage] = []

    struct SmsMessage: Identifiable, Codable {
        let id: UUID
        let sender: String
        let body: String
        let timestamp: Date
        var isRead: Bool = false
    }

    init() {
        loadMessages()
        setupBridgeMessages()
    }

    private func setupBridgeMessages() {
        // BridgeClient'ın onMessage'i CallManager tarafından da kullanılıyor
        // NotificationCenter üzerinden mesaj alıyoruz
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleBridgeNotification(_:)),
            name: .bridgeSmsReceived,
            object: nil
        )
    }

    @objc private func handleBridgeNotification(_ notification: Notification) {
        guard let data = notification.userInfo as? [String: Any] else { return }
        let sender = data["sender"] as? String ?? "Bilinmiyor"
        let body   = data["body"]   as? String ?? ""
        let ts     = data["timestamp"] as? Double ?? Date().timeIntervalSince1970 * 1000
        receiveMessage(sender: sender, body: body, timestamp: Date(timeIntervalSince1970: ts / 1000))
    }

    func receiveMessage(sender: String, body: String, timestamp: Date) {
        let msg = SmsMessage(id: UUID(), sender: sender, body: body, timestamp: timestamp)
        DispatchQueue.main.async {
            self.messages.insert(msg, at: 0)
            self.saveMessages()
        }
        showNotification(sender: sender, body: body)
    }

    func sendMessage(to: String, body: String) {
        BridgeClient.shared.send(type: "sms_send", data: [
            "to": to,
            "body": body
        ])
        // Gönderilen mesajı de listeye ekle
        let msg = SmsMessage(
            id: UUID(),
            sender: "Ben → \(to)",
            body: body,
            timestamp: Date(),
            isRead: true
        )
        DispatchQueue.main.async {
            self.messages.insert(msg, at: 0)
            self.saveMessages()
        }
    }

    private func showNotification(sender: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = sender
        content.body = body
        content.sound = .default
        content.categoryIdentifier = "SMS"

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    private func saveMessages() {
        if let data = try? JSONEncoder().encode(Array(messages.prefix(500))) {
            UserDefaults.standard.set(data, forKey: "sms_messages")
        }
    }

    private func loadMessages() {
        guard let data = UserDefaults.standard.data(forKey: "sms_messages"),
              let msgs = try? JSONDecoder().decode([SmsMessage].self, from: data) else { return }
        messages = msgs
    }
}

extension Notification.Name {
    static let bridgeSmsReceived = Notification.Name("bridgeSmsReceived")
}
