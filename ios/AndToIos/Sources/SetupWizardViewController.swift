import UIKit

class SetupWizardViewController: UIViewController {

    private var currentStep = 0

    private let steps: [(title: String, description: String, icon: String, instruction: String)] = [
        (
            title: "Hoş Geldiniz",
            description: "AndToIos, Android telefonunuzun hattını iPhone'unuza taşır. Kurulum yalnızca bir kez yapılır.",
            icon: "📱",
            instruction: "Başlamak için ileri tuşuna basın."
        ),
        (
            title: "1. Android'de Bluetooth Tethering",
            description: "Android telefonunuz iPhone'a Bluetooth üzerinden internet paylaşacak. Bu sayede operatör hotspot ücreti kesmez.",
            icon: "🔵",
            instruction: "Android'de:\nAyarlar → Bağlantılar → Mobil Hotspot ve Tethering → Bluetooth Tethering'i AÇ"
        ),
        (
            title: "2. Android'de Wi-Fi Hotspot",
            description: "Arama ve SMS köprüsü Wi-Fi üzerinden çalışacak. Android hotspot açması gerekiyor.",
            icon: "📶",
            instruction: "Android'de:\nAyarlar → Bağlantılar → Mobil Hotspot ve Tethering → Mobil Hotspot'u AÇ\n\nHotspot adı ve şifresini not alın."
        ),
        (
            title: "3. iPhone'da Bluetooth",
            description: "iPhone, Android'in Bluetooth tethering'ine bağlanacak. Bu sayede internet bağlantınız olacak.",
            icon: "🔗",
            instruction: "iPhone'da:\nAyarlar → Bluetooth → Android cihazınızı seçin → Bağlan\n\n'Kişisel Erişim Noktası' olarak bağlandığından emin olun."
        ),
        (
            title: "4. iPhone'da Wi-Fi",
            description: "iPhone, Android'in Wi-Fi hotspot'una da bağlanacak. Bu bağlantı üzerinden ses ve SMS geçecek.",
            icon: "📡",
            instruction: "iPhone'da:\nAyarlar → Wi-Fi → Android'in hotspot adını seçin → Bağlan\n\nNot: İki bağlantı aynı anda aktif olacak, bu normaldir."
        ),
        (
            title: "5. Android Uygulaması",
            description: "Android'deki AndToIos uygulamasını açın ve bridge'i başlatın.",
            icon: "⚙️",
            instruction: "Android'de AndToIos uygulamasını açın.\n\nTüm kontroller yeşil olunca 'Başlat' butonuna basın.\n\nEkranda görünen IP adresini not alın."
        ),
        (
            title: "6. IP Adresi",
            description: "Android uygulamasında görünen IP adresini buraya girin.",
            icon: "🔢",
            instruction: ""
        ),
        (
            title: "Hazır! 🎉",
            description: "Kurulum tamamlandı. Artık Android'inize gelen aramalar ve SMS'ler iPhone'unuzda görünecek.",
            icon: "✅",
            instruction: "• Gelen aramalar tam ekran olarak görünür\n• SMS'ler bildirim olarak gelir\n• Mesajlar sekmesinden SMS gönderebilirsiniz\n• Arama sekmesinden arayabilirsiniz"
        ),
    ]

    // MARK: - UI

    private let iconLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 64)
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let titleLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 24, weight: .bold)
        l.textColor = .white
        l.textAlignment = .center
        l.numberOfLines = 0
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let descLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 15)
        l.textColor = UIColor(white: 0.7, alpha: 1)
        l.textAlignment = .center
        l.numberOfLines = 0
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let instructionBox: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.18, alpha: 1)
        v.layer.cornerRadius = 12
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    private let instructionLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 14)
        l.textColor = UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        l.numberOfLines = 0
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let ipTextField: UITextField = {
        let tf = UITextField()
        tf.placeholder = "Android IP (örn: 192.168.43.1)"
        tf.borderStyle = .roundedRect
        tf.keyboardType = .numbersAndPunctuation
        tf.autocorrectionType = .no
        tf.backgroundColor = UIColor(red: 0.15, green: 0.15, blue: 0.22, alpha: 1)
        tf.textColor = .white
        tf.attributedPlaceholder = NSAttributedString(
            string: "Android IP (örn: 192.168.43.1)",
            attributes: [.foregroundColor: UIColor(white: 0.4, alpha: 1)]
        )
        tf.isHidden = true
        tf.translatesAutoresizingMaskIntoConstraints = false
        return tf
    }()

    private let progressView: UIProgressView = {
        let p = UIProgressView(progressViewStyle: .default)
        p.progressTintColor = UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        p.trackTintColor = UIColor(white: 0.2, alpha: 1)
        p.translatesAutoresizingMaskIntoConstraints = false
        return p
    }()

    private let progressLabel: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 12)
        l.textColor = UIColor(white: 0.4, alpha: 1)
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let btnNext: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("İleri →", for: .normal)
        b.backgroundColor = UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        b.setTitleColor(.black, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        b.layer.cornerRadius = 12
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    private let btnBack: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("← Geri", for: .normal)
        b.setTitleColor(UIColor(white: 0.5, alpha: 1), for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 15)
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.06, green: 0.06, blue: 0.1, alpha: 1)
        setupUI()
        updateStep(animated: false)
        btnNext.addTarget(self, action: #selector(nextTapped), for: .touchUpInside)
        btnBack.addTarget(self, action: #selector(backTapped), for: .touchUpInside)
        let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
        view.addGestureRecognizer(tap)
    }

    // MARK: - Actions

    @objc private func nextTapped() {
        // IP adımında kaydet
        if currentStep == 6 {
            guard let ip = ipTextField.text?.trimmingCharacters(in: .whitespaces), !ip.isEmpty else {
                shake(ipTextField)
                return
            }
            BridgeClient.shared.connect(ip: ip)
        }

        if currentStep < steps.count - 1 {
            currentStep += 1
            updateStep(animated: true)
        } else {
            // Kurulum tamamlandı
            UserDefaults.standard.set(true, forKey: "setup_completed")
            dismiss(animated: true)
        }
    }

    @objc private func backTapped() {
        if currentStep > 0 {
            currentStep -= 1
            updateStep(animated: true)
        }
    }

    @objc private func dismissKeyboard() { view.endEditing(true) }

    // MARK: - Step Update

    private func updateStep(animated: Bool) {
        let step = steps[currentStep]
        let total = steps.count

        let update = {
            self.iconLabel.text = step.icon
            self.titleLabel.text = step.title
            self.descLabel.text = step.description
            self.instructionLabel.text = step.instruction
            self.instructionBox.isHidden = step.instruction.isEmpty
            self.ipTextField.isHidden = self.currentStep != 6
            self.progressView.setProgress(Float(self.currentStep + 1) / Float(total), animated: animated)
            self.progressLabel.text = "\(self.currentStep + 1) / \(total)"
            self.btnBack.isHidden = self.currentStep == 0
            let isLast = self.currentStep == total - 1
            self.btnNext.setTitle(isLast ? "Başla! 🚀" : "İleri →", for: .normal)
            self.btnNext.backgroundColor = isLast
                ? UIColor(red: 0.18, green: 0.8, blue: 0.44, alpha: 1)
                : UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        }

        if animated {
            UIView.transition(with: view, duration: 0.25, options: .transitionCrossDissolve, animations: update)
        } else {
            update()
        }
    }

    private func shake(_ view: UIView) {
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.x")
        animation.timingFunction = CAMediaTimingFunction(name: .linear)
        animation.duration = 0.4
        animation.values = [-10, 10, -8, 8, -5, 5, 0]
        view.layer.add(animation, forKey: "shake")
    }

    // MARK: - Layout

    private func setupUI() {
        instructionBox.addSubview(instructionLabel)
        [iconLabel, titleLabel, descLabel, instructionBox,
         ipTextField, progressView, progressLabel, btnNext, btnBack].forEach {
            view.addSubview($0)
        }

        NSLayoutConstraint.activate([
            progressView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            progressView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            progressView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),
            progressView.heightAnchor.constraint(equalToConstant: 4),

            progressLabel.topAnchor.constraint(equalTo: progressView.bottomAnchor, constant: 6),
            progressLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            iconLabel.topAnchor.constraint(equalTo: progressLabel.bottomAnchor, constant: 32),
            iconLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            titleLabel.topAnchor.constraint(equalTo: iconLabel.bottomAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            descLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            descLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            descLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            instructionBox.topAnchor.constraint(equalTo: descLabel.bottomAnchor, constant: 24),
            instructionBox.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            instructionBox.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            instructionLabel.topAnchor.constraint(equalTo: instructionBox.topAnchor, constant: 16),
            instructionLabel.leadingAnchor.constraint(equalTo: instructionBox.leadingAnchor, constant: 16),
            instructionLabel.trailingAnchor.constraint(equalTo: instructionBox.trailingAnchor, constant: -16),
            instructionLabel.bottomAnchor.constraint(equalTo: instructionBox.bottomAnchor, constant: -16),

            ipTextField.topAnchor.constraint(equalTo: descLabel.bottomAnchor, constant: 24),
            ipTextField.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            ipTextField.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            ipTextField.heightAnchor.constraint(equalToConstant: 48),

            btnNext.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            btnNext.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            btnNext.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            btnNext.heightAnchor.constraint(equalToConstant: 52),

            btnBack.bottomAnchor.constraint(equalTo: btnNext.topAnchor, constant: -12),
            btnBack.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        ])
    }
}
