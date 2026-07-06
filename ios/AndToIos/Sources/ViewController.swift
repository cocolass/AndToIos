import UIKit
import Combine

class ViewController: UIViewController {

    private var cancellables = Set<AnyCancellable>()

    // MARK: - UI Elements

    private let scrollView = UIScrollView()
    private let contentView = UIView()

    private let lblTitle: UILabel = {
        let l = UILabel()
        l.text = "AndToIos"
        l.font = .systemFont(ofSize: 32, weight: .bold)
        l.textColor = .white
        l.textAlignment = .center
        return l
    }()

    private let lblSubtitle: UILabel = {
        let l = UILabel()
        l.text = "Android → iPhone Köprüsü"
        l.font = .systemFont(ofSize: 14)
        l.textColor = UIColor(white: 0.6, alpha: 1)
        l.textAlignment = .center
        return l
    }()

    private let statusCard = CardView()
    private let lblStatus: UILabel = {
        let l = UILabel()
        l.text = "● Bağlantı bekleniyor"
        l.font = .systemFont(ofSize: 15, weight: .medium)
        l.textColor = UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        return l
    }()

    private let settingsCard = CardView()
    private let tfAndroidIP: UITextField = {
        let tf = UITextField()
        tf.placeholder = "Android IP (örn: 192.168.43.1)"
        tf.borderStyle = .none
        tf.font = .systemFont(ofSize: 15)
        tf.textColor = .white
        tf.keyboardType = .numbersAndPunctuation
        tf.autocorrectionType = .no
        tf.attributedPlaceholder = NSAttributedString(
            string: "Android IP (örn: 192.168.43.1)",
            attributes: [.foregroundColor: UIColor(white: 0.4, alpha: 1)]
        )
        return tf
    }()

    private let btnConnect: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("Bağlan", for: .normal)
        b.backgroundColor = UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        b.setTitleColor(.black, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        b.layer.cornerRadius = 10
        return b
    }()

    private let smsCard = CardView()
    private let lblSmsCount: UILabel = {
        let l = UILabel()
        l.text = "SMS: 0 mesaj"
        l.font = .systemFont(ofSize: 15)
        l.textColor = .white
        return l
    }()

    private let btnSms: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("Mesajları Görüntüle →", for: .normal)
        b.setTitleColor(UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1), for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 14)
        return b
    }()

    private let callCard = CardView()
    private let tfCallNumber: UITextField = {
        let tf = UITextField()
        tf.placeholder = "Numara girin"
        tf.borderStyle = .none
        tf.font = .systemFont(ofSize: 15)
        tf.textColor = .white
        tf.keyboardType = .phonePad
        tf.attributedPlaceholder = NSAttributedString(
            string: "Numara girin",
            attributes: [.foregroundColor: UIColor(white: 0.4, alpha: 1)]
        )
        return tf
    }()

    private let btnCall: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("📞  Ara", for: .normal)
        b.backgroundColor = UIColor(red: 0.18, green: 0.8, blue: 0.44, alpha: 1)
        b.setTitleColor(.white, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        b.layer.cornerRadius = 10
        return b
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.06, green: 0.06, blue: 0.1, alpha: 1)
        setupUI()
        setupActions()
        bindObservables()

        // Kaydedilmiş IP'yi yükle
        tfAndroidIP.text = UserDefaults.standard.string(forKey: "android_ip") ?? ""
    }

    // MARK: - Bind

    private func bindObservables() {
        BridgeClient.shared.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] connected in
                self?.lblStatus.text = connected
                    ? "● Bağlı — Android hazır"
                    : "● Bağlantı bekleniyor"
                self?.lblStatus.textColor = connected
                    ? UIColor(red: 0.18, green: 0.8, blue: 0.44, alpha: 1)
                    : UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
            }
            .store(in: &cancellables)

        SmsManager.shared.$messages
            .receive(on: DispatchQueue.main)
            .sink { [weak self] msgs in
                let unread = msgs.filter { !$0.isRead }.count
                self?.lblSmsCount.text = unread > 0
                    ? "SMS: \(unread) okunmamış mesaj"
                    : "SMS: \(msgs.count) mesaj"
            }
            .store(in: &cancellables)
    }

    // MARK: - Actions

    private func setupActions() {
        btnConnect.addTarget(self, action: #selector(connectTapped), for: .touchUpInside)
        btnCall.addTarget(self, action: #selector(callTapped), for: .touchUpInside)
        btnSms.addTarget(self, action: #selector(smsTapped), for: .touchUpInside)
        let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
        view.addGestureRecognizer(tap)
    }

    @objc private func connectTapped() {
        guard let ip = tfAndroidIP.text?.trimmingCharacters(in: .whitespaces), !ip.isEmpty else {
            showAlert("Android IP adresi boş olamaz")
            return
        }
        BridgeClient.shared.connect(ip: ip)
        view.endEditing(true)
    }

    @objc private func callTapped() {
        guard BridgeClient.shared.isConnected else {
            showAlert("Önce Android'e bağlanın")
            return
        }
        guard let number = tfCallNumber.text?.trimmingCharacters(in: .whitespaces), !number.isEmpty else {
            showAlert("Numara boş olamaz")
            return
        }
        CallManager.shared.makeCall(number: number)
        tfCallNumber.text = ""
        view.endEditing(true)
    }

    @objc private func smsTapped() {
        let smsVC = SmsViewController()
        navigationController?.pushViewController(smsVC, animated: true)
    }

    @objc private func dismissKeyboard() { view.endEditing(true) }

    private func showAlert(_ msg: String) {
        let alert = UIAlertController(title: nil, message: msg, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Tamam", style: .default))
        present(alert, animated: true)
    }

    // MARK: - Layout

    private func setupUI() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        scrollView.addSubview(contentView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            contentView.topAnchor.constraint(equalTo: scrollView.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.widthAnchor),
        ])

        let views: [UIView] = [lblTitle, lblSubtitle, statusCard, settingsCard, callCard, smsCard]
        views.forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            contentView.addSubview($0)
        }

        // Status card içeriği
        [lblStatus].forEach { statusCard.addContent($0) }

        // Settings card içeriği
        let ipLabel = makeLabel("Android IP Adresi")
        let ipSep = makeSeparator()
        tfAndroidIP.translatesAutoresizingMaskIntoConstraints = false
        btnConnect.translatesAutoresizingMaskIntoConstraints = false
        settingsCard.addContent(ipLabel)
        settingsCard.addContent(ipSep)
        settingsCard.addContent(tfAndroidIP)
        settingsCard.addContent(btnConnect)

        // Call card içeriği
        let callLabel = makeLabel("Arama Yap")
        let callSep = makeSeparator()
        tfCallNumber.translatesAutoresizingMaskIntoConstraints = false
        btnCall.translatesAutoresizingMaskIntoConstraints = false
        callCard.addContent(callLabel)
        callCard.addContent(callSep)
        callCard.addContent(tfCallNumber)
        callCard.addContent(btnCall)

        // SMS card içeriği
        let smsLabel = makeLabel("Mesajlar")
        let smsSep = makeSeparator()
        smsCard.addContent(smsLabel)
        smsCard.addContent(smsSep)
        smsCard.addContent(lblSmsCount)
        smsCard.addContent(btnSms)

        let pad: CGFloat = 20

        NSLayoutConstraint.activate([
            lblTitle.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 32),
            lblTitle.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),

            lblSubtitle.topAnchor.constraint(equalTo: lblTitle.bottomAnchor, constant: 4),
            lblSubtitle.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),

            statusCard.topAnchor.constraint(equalTo: lblSubtitle.bottomAnchor, constant: 24),
            statusCard.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: pad),
            statusCard.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -pad),

            settingsCard.topAnchor.constraint(equalTo: statusCard.bottomAnchor, constant: 16),
            settingsCard.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: pad),
            settingsCard.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -pad),

            callCard.topAnchor.constraint(equalTo: settingsCard.bottomAnchor, constant: 16),
            callCard.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: pad),
            callCard.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -pad),

            smsCard.topAnchor.constraint(equalTo: callCard.bottomAnchor, constant: 16),
            smsCard.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: pad),
            smsCard.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -pad),
            smsCard.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -32),
        ])
    }

    private func makeLabel(_ text: String) -> UILabel {
        let l = UILabel()
        l.text = text
        l.font = .systemFont(ofSize: 13, weight: .semibold)
        l.textColor = UIColor(white: 0.5, alpha: 1)
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }

    private func makeSeparator() -> UIView {
        let v = UIView()
        v.backgroundColor = UIColor(white: 0.2, alpha: 1)
        v.translatesAutoresizingMaskIntoConstraints = false
        v.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return v
    }
}

// MARK: - CardView

class CardView: UIView {
    private let stack: UIStackView = {
        let s = UIStackView()
        s.axis = .vertical
        s.spacing = 12
        s.translatesAutoresizingMaskIntoConstraints = false
        return s
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.16, alpha: 1)
        layer.cornerRadius = 14
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func addContent(_ view: UIView) {
        stack.addArrangedSubview(view)
        if let btn = view as? UIButton {
            btn.heightAnchor.constraint(equalToConstant: 44).isActive = true
        }
    }
}
