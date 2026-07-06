# AndToIos

Android SIM kartını iPhone'a köprüleyen uygulama.
Gelen aramalar, giden aramalar ve SMS — hepsi iPhone üzerinden.

## Android Kurulum
1. GitHub Actions ile APK build et (Actions sekmesi → Run workflow)
2. APK'yı Reeder'a yükle
3. Uygulamayı aç, tüm adımları tamamla:
   - İzinler ver
   - Pil optimizasyonunu kapat
   - Varsayılan telefon uygulaması yap
   - Varsayılan SMS uygulaması yap
4. "Başlat" butonuna bas
5. Ekranda görünen IP adresini not al

## iOS Kurulum
1. Mac'te terminal aç, `ios/` klasörüne gir
2. `pod install` çalıştır
3. `AndToIos.xcworkspace` dosyasını Xcode ile aç
4. Signing & Capabilities → kendi Apple hesabını seç
5. Capabilities ekle: Background Modes → Voice over IP + Audio
6. iPhone'u bağla → Run (▶)
7. Uygulamayı aç, Android'in IP adresini gir → Bağlan

## Nasıl Çalışır
- Android hotspot açar, iPhone bağlanır
- WebSocket üzerinden köprü kurulur
- Ses WebRTC ile taşınır (sıfır gecikme)
- SMS JSON mesajlaşma ile iletilir
- Gelen aramalar CallKit ile tam ekran gösterilir
