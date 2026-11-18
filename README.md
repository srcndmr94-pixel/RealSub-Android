# RealSub (Android)

Ekrandaki İngilizce altyazıyı okur, **sokak ağzına ve küfre sadık** Türkçesini ekranın üstünde gösterir.
Hangi uygulamayı kullandığın fark etmez: YouTube, oyun, yerel oynatıcı — altyazı her şeyin üstünde durur.

## Nasıl çalışır

1. Ekran kaydı izniyle ekranın görüntüsü alınır (hiçbir yere gönderilmez, kayıt tutulmaz).
2. Yalnızca senin seçtiğin **altyazı alanı** kesilip okunur.
3. Okuma cihazın içinde yapılır (ML Kit) — internet gerekmez, ücretsizdir.
4. Metin değiştiğinde, yalnızca o zaman çeviri istenir. Sahnedeki hareket istek tetiklemez.
5. Çeviri, ekranın üstünde duran katmanda görünür: Türkçe üstte, istersen İngilizce altta.

## Kurulum

1. GitHub'da **Actions** sekmesinden son çalışmayı aç.
2. **Artifacts** bölümünden `RealSub-APK` dosyasını indir, içinden `app-debug.apk` çıkar.
3. Telefona kur ("bilinmeyen kaynak" uyarısına izin ver).
4. Uygulamayı aç:
   - **Ekranın üstüne çizme iznini ver** — zorunlu.
   - **API anahtarı** — Google AI Studio'dan ücretsiz alınır. Boş bırakırsan ücretsiz motor kullanılır.
   - **Alanı seç** — altyazının çıktığı yeri parmağınla çerçevele.
   - **Başlat** — ekran kaydı iznini onayla, uygulamadan çık, izlemeye başla.
5. Durdurmak için bildirime dokun.

## Bir şey görünmüyorsa

Uygulamadaki **5. Durum** bölümü akışın neresinde takıldığını söyler. Çalışırken uygulamaya dön,
**Tek kare oku** → **Durumu yenile**:

| Gördüğün | Anlamı |
|---|---|
| Alınan kare 0 | Ekran kaydı başlamamış; Başlat'a tekrar bas |
| Parlak nokta çok düşük | Seçili alan yanlış yerde ya da altyazı o anda ekranda değil |
| Okuma denemesi var, "son okunan: (okunamadı)" | Alan doğru ama yazı küçük/bulanık; alanı daha dar seç |
| Son okunan dolu, son çeviri boş | Okuma çalışıyor, çeviri takılıyor — "son hata" satırına bak |

## Sertlik seviyeleri

| Seviye | Örnek: *"Fuck you, bitch."* |
|---|---|
| Yumuşak | Canın cehenneme, cadaloz. |
| Doğal | Defol, sürtük. |
| **Sokak ağzı** (varsayılan) | Siktir git, kaltak. |
| Tam sansürsüz | Siktir git amına koyayım, orospu. |

Yerleşik sözlük hem ücretsiz motorda doğrudan uygulanır, hem yapay zekâya kural olarak verilir.

## Bilinen sınırlar

- **Netflix, Disney+ gibi uygulamalar** ekran kaydını engeller (FLAG_SECURE); o uygulamalarda kare siyah gelir ve okuma yapılamaz. Bu Android'in kuralı, aşılamaz.
- Her yeni altyazı satırı bir çeviri isteğidir. Ücretsiz Gemini kotasında dakikalık sınır vardır.
- Okuma cihaz üstünde yapıldığı için altyazı küçük ya da çok süslü yazı tipindeyse hata payı artar.

## Derleme

GitHub Actions her `main` push'unda APK üretir: `.github/workflows/build.yml`.
Bilgisayarda derlemek istersen: Android Studio ile aç, `:app:assembleDebug`.

## Yapı

| Dosya | İşi |
|---|---|
| `MainActivity.kt` | Ayar ekranı, izinler, başlat/durdur |
| `RegionActivity.kt` | Altyazı alanını parmakla seçme |
| `CaptureService.kt` | Ekran kaydı, değişim algılama, akışın yönetimi |
| `Ocr.kt` | Cihaz içi metin okuma ve temizleme |
| `Translator.kt` | Gemini çevirisi + ücretsiz motor |
| `Slang.kt` | Sokak ağzı sözlüğü, dört sertlik seviyesi |
| `Overlay.kt` | Ekranın üstündeki altyazı katmanı |
| `Prefs.kt` | Ayarların saklanması |
