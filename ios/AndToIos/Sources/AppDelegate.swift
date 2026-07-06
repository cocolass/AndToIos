import UIKit
import PushKit
import UserNotifications

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    private var voipRegistry: PKPushRegistry!

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {

        _ = CallManager.shared
        _ = SmsManager.shared

        // Bildirim izni
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        UNUserNotificationCenter.current().delegate = self

        // PushKit
        voipRegistry = PKPushRegistry(queue: .main)
        voipRegistry.delegate = self
        voipRegistry.desiredPushTypes = [.voIP]

        // Ana pencere
        window = UIWindow(frame: UIScreen.main.bounds)
        let mainVC = UINavigationController(rootViewController: ViewController())
        window?.rootViewController = mainVC
        window?.makeKeyAndVisible()

        // İlk kurulum sihirbazı
        let setupDone = UserDefaults.standard.bool(forKey: "setup_completed")
        if !setupDone {
            let wizard = SetupWizardViewController()
            wizard.modalPresentationStyle = .fullScreen
            mainVC.present(wizard, animated: false)
        } else {
            // Kaydedilmiş IP ile bağlan
            if let ip = UserDefaults.standard.string(forKey: "android_ip"), !ip.isEmpty {
                BridgeClient.shared.connect(ip: ip)
            }
        }

        // Bridge mesajlarını yönlendir
        BridgeClient.shared.onMessage = { type, data in
            switch type {
            case "call_incoming":
                let caller = data["caller"] as? String ?? "Bilinmiyor"
                let sdp    = data["sdp"]    as? String ?? ""
                CallManager.shared.handleIncomingCall(caller: caller, sdpOffer: sdp)
            case "call_ended":
                CallManager.shared.endAllCalls()
            case "sms_incoming":
                NotificationCenter.default.post(
                    name: .bridgeSmsReceived,
                    object: nil,
                    userInfo: data
                )
            default: break
            }
        }

        return true
    }
}

extension AppDelegate: PKPushRegistryDelegate {
    func pushRegistry(_ registry: PKPushRegistry, didUpdate credentials: PKPushCredentials, for type: PKPushType) {}

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload,
                      for type: PKPushType, completion: @escaping () -> Void) {
        let caller = payload.dictionaryPayload["caller"] as? String ?? "Bilinmiyor"
        let sdp    = payload.dictionaryPayload["sdp"]    as? String ?? ""
        CallManager.shared.handleIncomingCall(caller: caller, sdpOffer: sdp)
        completion()
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
