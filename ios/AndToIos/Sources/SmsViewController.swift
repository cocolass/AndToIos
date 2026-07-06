import UIKit
import Combine

class SmsViewController: UIViewController {

    private var cancellables = Set<AnyCancellable>()
    private var messages: [SmsManager.SmsMessage] = []

    private let tableView: UITableView = {
        let tv = UITableView()
        tv.backgroundColor = UIColor(red: 0.06, green: 0.06, blue: 0.1, alpha: 1)
        tv.separatorColor = UIColor(white: 0.15, alpha: 1)
        tv.translatesAutoresizingMaskIntoConstraints = false
        return tv
    }()

    private let composeView: UIView = {
        let v = UIView()
        v.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.16, alpha: 1)
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    private let tfTo: UITextField = {
        let tf = UITextField()
        tf.placeholder = "Alıcı numara"
        tf.borderStyle = .roundedRect
        tf.keyboardType = .phonePad
        tf.backgroundColor = UIColor(red: 0.15, green: 0.15, blue: 0.22, alpha: 1)
        tf.textColor = .white
        tf.attributedPlaceholder = NSAttributedString(
            string: "Alıcı numara",
            attributes: [.foregroundColor: UIColor(white: 0.4, alpha: 1)]
        )
        tf.translatesAutoresizingMaskIntoConstraints = false
        return tf
    }()

    private let tfMessage: UITextField = {
        let tf = UITextField()
        tf.placeholder = "Mesaj yazın..."
        tf.borderStyle = .roundedRect
        tf.backgroundColor = UIColor(red: 0.15, green: 0.15, blue: 0.22, alpha: 1)
        tf.textColor = .white
        tf.attributedPlaceholder = NSAttributedString(
            string: "Mesaj yazın...",
            attributes: [.foregroundColor: UIColor(white: 0.4, alpha: 1)]
        )
        tf.translatesAutoresizingMaskIntoConstraints = false
        return tf
    }()

    private let btnSend: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("Gönder", for: .normal)
        b.backgroundColor = UIColor(red: 0.3, green: 0.76, blue: 0.97, alpha: 1)
        b.setTitleColor(.black, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        b.layer.cornerRadius = 8
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Mesajlar"
        view.backgroundColor = UIColor(red: 0.06, green: 0.06, blue: 0.1, alpha: 1)
        navigationController?.navigationBar.barTintColor = UIColor(red: 0.06, green: 0.06, blue: 0.1, alpha: 1)
        navigationController?.navigationBar.titleTextAttributes = [.foregroundColor: UIColor.white]

        setupUI()
        bindMessages()

        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(SmsCell.self, forCellReuseIdentifier: "SmsCell")

        btnSend.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)

        NotificationCenter.default.addObserver(
            self, selector: #selector(keyboardWillShow(_:)),
            name: UIResponder.keyboardWillShowNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(keyboardWillHide(_:)),
            name: UIResponder.keyboardWillHideNotification, object: nil
        )
    }

    private func bindMessages() {
        SmsManager.shared.$messages
            .receive(on: DispatchQueue.main)
            .sink { [weak self] msgs in
                self?.messages = msgs
                self?.tableView.reloadData()
            }
            .store(in: &cancellables)
    }

    @objc private func sendTapped() {
        guard let to   = tfTo.text?.trimmingCharacters(in: .whitespaces), !to.isEmpty,
              let body = tfMessage.text?.trimmingCharacters(in: .whitespaces), !body.isEmpty else {
            return
        }
        guard BridgeClient.shared.isConnected else {
            let alert = UIAlertController(title: "Bağlantı Yok", message: "Android'e bağlı değilsiniz", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Tamam", style: .default))
            present(alert, animated: true)
            return
        }
        SmsManager.shared.sendMessage(to: to, body: body)
        tfMessage.text = ""
        view.endEditing(true)
    }

    @objc private func keyboardWillShow(_ n: Notification) {
        if let frame = n.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
            view.frame.origin.y = -frame.height / 2
        }
    }

    @objc private func keyboardWillHide(_ n: Notification) {
        view.frame.origin.y = 0
    }

    private func setupUI() {
        view.addSubview(tableView)
        view.addSubview(composeView)
        composeView.addSubview(tfTo)
        composeView.addSubview(tfMessage)
        composeView.addSubview(btnSend)

        let sep = UIView()
        sep.backgroundColor = UIColor(white: 0.15, alpha: 1)
        sep.translatesAutoresizingMaskIntoConstraints = false
        composeView.addSubview(sep)

        NSLayoutConstraint.activate([
            composeView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            composeView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            composeView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),

            sep.topAnchor.constraint(equalTo: composeView.topAnchor),
            sep.leadingAnchor.constraint(equalTo: composeView.leadingAnchor),
            sep.trailingAnchor.constraint(equalTo: composeView.trailingAnchor),
            sep.heightAnchor.constraint(equalToConstant: 1),

            tfTo.topAnchor.constraint(equalTo: composeView.topAnchor, constant: 12),
            tfTo.leadingAnchor.constraint(equalTo: composeView.leadingAnchor, constant: 12),
            tfTo.trailingAnchor.constraint(equalTo: composeView.trailingAnchor, constant: -12),
            tfTo.heightAnchor.constraint(equalToConstant: 36),

            tfMessage.topAnchor.constraint(equalTo: tfTo.bottomAnchor, constant: 8),
            tfMessage.leadingAnchor.constraint(equalTo: composeView.leadingAnchor, constant: 12),
            tfMessage.trailingAnchor.constraint(equalTo: btnSend.leadingAnchor, constant: -8),
            tfMessage.heightAnchor.constraint(equalToConstant: 36),
            tfMessage.bottomAnchor.constraint(equalTo: composeView.bottomAnchor, constant: -12),

            btnSend.trailingAnchor.constraint(equalTo: composeView.trailingAnchor, constant: -12),
            btnSend.centerYAnchor.constraint(equalTo: tfMessage.centerYAnchor),
            btnSend.widthAnchor.constraint(equalToConstant: 72),
            btnSend.heightAnchor.constraint(equalToConstant: 36),

            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: composeView.topAnchor),
        ])
    }
}

// MARK: - TableView

extension SmsViewController: UITableViewDelegate, UITableViewDataSource {

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        messages.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "SmsCell", for: indexPath) as! SmsCell
        cell.configure(with: messages[indexPath.row])
        return cell
    }
}

// MARK: - SmsCell

class SmsCell: UITableViewCell {

    private let lblSender: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 14, weight: .semibold)
        l.textColor = .white
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let lblBody: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 13)
        l.textColor = UIColor(white: 0.7, alpha: 1)
        l.numberOfLines = 2
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let lblTime: UILabel = {
        let l = UILabel()
        l.font = .systemFont(ofSize: 11)
        l.textColor = UIColor(white: 0.4, alpha: 1)
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = UIColor(red: 0.06, green: 0.06, blue: 0.1, alpha: 1)
        selectionStyle = .none
        contentView.addSubview(lblSender)
        contentView.addSubview(lblBody)
        contentView.addSubview(lblTime)
        NSLayoutConstraint.activate([
            lblSender.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 12),
            lblSender.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            lblSender.trailingAnchor.constraint(equalTo: lblTime.leadingAnchor, constant: -8),

            lblTime.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 12),
            lblTime.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),

            lblBody.topAnchor.constraint(equalTo: lblSender.bottomAnchor, constant: 4),
            lblBody.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            lblBody.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            lblBody.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -12),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func configure(with msg: SmsManager.SmsMessage) {
        lblSender.text = msg.sender
        lblBody.text = msg.body
        let fmt = DateFormatter()
        fmt.dateFormat = "dd.MM HH:mm"
        lblTime.text = fmt.string(from: msg.timestamp)
        lblSender.textColor = msg.isRead ? UIColor(white: 0.7, alpha: 1) : .white
    }
}
