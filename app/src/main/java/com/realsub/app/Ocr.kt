package com.realsub.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cihaz içi metin okuma ve "bu gerçekten altyazı mı" ayıklaması.
 * İnternet gerekmez, ücretsizdir.
 *
 * Eleme kuralları GameLingo'da sahada ayıklanmış kurallardan alınmıştır:
 * oynatıcı arayüzü, tuş uyarıları, konsol durum çubuğu, parçalanmış okuma
 * ve en önemlisi programın ekrana kendi yazdığı Türkçe metin elenir.
 */
object Ocr {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Bulunan altyazı: metni ve tarama görüntüsündeki dikey yeri. */
    class Found(val text: String, val top: Int, val bottom: Int)

    // ------------------------------------------------------------------ sabitler

    private const val TURKISH_ONLY = "ığşİĞŞ"

    /** Yalnızca Türkçede sık geçen kelimeler: satır zaten Türkçeyse çevrilmez. */
    private val TURKCE_KELIMELER = setOf(
        "bir", "bu", "şu", "için", "ile", "çok", "daha", "var", "yok",
        "değil", "ama", "gibi", "kadar", "sonra", "önce", "şey", "evet",
        "hayır", "tamam", "devam", "edin", "halinde", "abone", "izlemeye",
        "ve", "veya", "ben", "sen", "biz", "siz", "onlar", "bizim", "senin",
        "benim", "nasıl", "neden", "nerede", "kim", "hangi", "oldu", "olur",
        "yapıyor", "geldi", "gitti", "dedi", "bilmiyorum", "görünüm", "diğer",
        "göster", "ürünleri", "videolar", "yorumlar", "paylaş", "kaydet"
    )

    /** Altyazı olmayan, ama altyazı bölgesine düşebilen yazılar. */
    private val BLOCK_PHRASES = listOf(
        // Android ekran kaydı uyarısı (Netflix, Prime vb.)
        "güvenlik nedeniyle", "uygulama içerikleri", "gizlendi",
        "secure content", "content is hidden",
        // Oynatıcı arayüzü
        "dialogue boost", "x-ray", "xray", "ekran görüntüsü",
        "alt yazı", "alt yazi", "altyazılar", "subtitles",
        "audio and subtitles", "audio & subtitles",
        "skip intro", "skip recap", "next episode", "watch credits",
        "episodes", "bölümler", "ses artırma", "ayarlar ve",
        // YouTube
        "ürünleri göster", "diğer videolar", "abone ol", "otomatik oynat",
        "daha sonra izle", "oynatma listesi", "izlenme", "show products",
        "watch later", "up next", "autoplay", "subscribe", "shorts",
        "sonraki video", "canlı yayın", "video kalitesi", "genişletilmiş",
        // Konsol / uzaktan oynatma
        "playstation", "play station", "xbox", "nintendo", "steam deck",
        "remote play", "uzaktan oynat", "dualsense", "ps remote"
    )

    private val UI_WORDS = setOf(
        "TOUCHPAD", "OPTIONS", "SKIP", "CONTINUE", "LOADING", "AUTOSAVE",
        "OBJECTIVE", "NEW OBJECTIVE", "MISSION", "CHECKPOINT"
    )

    /** Cümle kuran kelimeler: biri varsa satır repliktir, liste değildir. */
    private val SENTENCE_WORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "i", "you", "he", "she", "we", "they", "it", "me", "him", "her",
        "my", "your", "his", "our", "their", "this", "that", "these",
        "to", "of", "in", "on", "at", "for", "with", "from", "by",
        "and", "or", "but", "if", "so", "not", "no", "yes",
        "do", "does", "did", "don't", "can", "can't", "will", "won't",
        "have", "has", "had", "get", "got", "go", "come", "know", "think",
        "what", "why", "how", "when", "where", "who"
    )

    /** Tek-iki harfli GERÇEK kelimeler; bunlar "kırıntı" sayılmaz. */
    private val KISA_KELIMELER = setOf(
        "a", "i", "am", "an", "as", "at", "be", "by", "do", "go", "he",
        "hi", "if", "in", "is", "it", "me", "my", "no", "of", "oh", "ok",
        "on", "or", "so", "to", "up", "us", "we", "ya", "yo", "ye", "ah",
        "ay", "eh", "uh", "um", "aw", "ow", "oi", "ha", "hm", "mm", "nah",
        "m", "s", "t", "d", "ll", "re", "ve", "em", "im", "id"
    )

    /** "Press O to throw", "Hold R2 to aim" türü tuş uyarıları. */
    private val TUS_UYARISI = Regex(
        "(?i)\\b(press|hold|tap|push)\\s+(?:and\\s+hold\\s+)?(?:the\\s+)?" +
                "([a-z]|[lr][123]|△|○|□|✕|circle|square|triangle|cross|touchpad|options)\\b"
    )

    /** Tek başına duran büyük harfler: HUD düğme simgeleri. I ve A sayılmaz. */
    private val TEK_HARF_SIMGE = Regex("(?<![\\p{L}'])[B-HJ-Z](?![\\p{L}'])")

    // ---------------------------------------------- kendi yazdığımız metnin takibi

    private val displayed = ArrayDeque<String>()

    /** Ekrana yazdığımız çeviri kaydedilir; sonraki karede tekrar okunursa elenir. */
    @Synchronized
    fun markDisplayed(tr: String) {
        val n = normalize(tr)
        if (n.length < 4) return
        displayed.addLast(n)
        while (displayed.size > 8) displayed.removeFirst()
    }

    @Synchronized
    fun forgetDisplayed() = displayed.clear()

    private fun normalize(s: String): String = s.lowercase()
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .replace('â', 'a').replace('î', 'i').replace('û', 'u')
        .filter { it.isLetterOrDigit() }

    /** Satır zaten Türkçe mi? Öyleyse çevirmenin anlamı yok. */
    private fun zatenTurkce(text: String): Boolean {
        if (text.any { it in TURKISH_ONLY }) return true
        for (k in text.lowercase().split(Regex("[^\\p{L}]+"))) {
            if (k.length >= 2 && k in TURKCE_KELIMELER) return true
        }
        return false
    }

    /**
     * Ekranda bizim yazdığımız Türkçe metin mi?
     * Kendi yazımız yeniden okunursa metin her turda büyüyerek geri gelir;
     * parça eşleşmesi bu döngüyü kırar.
     */
    @Synchronized
    private fun isOurOwnText(text: String): Boolean {
        if (zatenTurkce(text)) return true
        val simdi = normalize(text)
        if (simdi.isEmpty()) return false
        for (eski in displayed) {
            if (eski == simdi) return true
            // Parça eşleşmesi yalnızca iki taraf da uzunsa geçerli: kısa bir
            // replik ("Run!" -> "run") uzun bir Türkçe cümlenin içinde rastgele
            // geçebiliyor ve gerçek altyazı elenmiş oluyordu.
            if (simdi.length < 8 || eski.length < 8) continue
            val kisa = minOf(simdi.length, eski.length)
            val uzun = maxOf(simdi.length, eski.length)
            if (kisa * 10 >= uzun * 4 && (simdi.contains(eski) || eski.contains(simdi))) return true
        }
        return false
    }

    // ------------------------------------------------------------------ okuma

    fun detect(bmp: Bitmap, auto: Boolean, band: Float = -1f, bandTol: Float = 0f): Found? {
        val latch = CountDownLatch(1)
        var lines: List<Pair<String, Rect>> = emptyList()
        try {
            client.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { r ->
                    val acc = ArrayList<Pair<String, Rect>>()
                    for (block in r.textBlocks) for (ln in block.lines) {
                        val box = ln.boundingBox ?: continue
                        if (ln.text.isNotBlank()) acc.add(ln.text to box)
                    }
                    lines = acc
                    latch.countDown()
                }
                .addOnFailureListener { latch.countDown() }
        } catch (e: Exception) {
            return null
        }
        latch.await(6, TimeUnit.SECONDS)
        if (lines.isEmpty()) return null

        val w = bmp.width
        val h = bmp.height

        // Çok küçük ya da çok dar parçalar atılır.
        var pool = lines.filter { (_, b) ->
            b.height() >= maxOf(9, (h * 0.03f).toInt()) && b.width() >= w * 0.04f
        }
        if (pool.isEmpty()) return null

        if (auto) {
            // Ortaya yakın duranlar altyazıdır; kenarda kalanlar arayüzdür.
            val cx = w / 2f
            val central = pool.filter { (_, b) -> Math.abs((b.left + b.right) / 2f - cx) < w * 0.42f }
            if (central.isNotEmpty()) pool = central

            // Şerit biliniyorsa dışına bakılmaz.
            if (band >= 0f) {
                val inBand = pool.filter { (_, b) -> Math.abs((b.top + b.bottom) / 2f - band) <= bandTol }
                if (inBand.isEmpty()) return null
                pool = inBand
            }
        }

        // En alttaki küme: altyazı en fazla üç satırdır, araları açılırsa ayrı bloktur.
        val group = enAlttakiKume(pool.sortedBy { it.second.top }, h)
        if (group.isEmpty()) return null

        val text = temizle(group.joinToString(" ") { it.first })
        if (text.isEmpty()) return null
        if (isOurOwnText(text)) return null
        if (altyaziDegil(text)) return null
        if (arayuzYazisi(text)) return null

        return Found(text, group.first().second.top, group.last().second.bottom)
    }

    /** Alttan yukarı doğru, araları fazla açılmayan en çok üç satır. */
    private fun enAlttakiKume(
        satirlar: List<Pair<String, Rect>>,
        seritYuksekligi: Int
    ): List<Pair<String, Rect>> {
        if (satirlar.size <= 1) return satirlar
        val boslukEsigi = (seritYuksekligi * 0.14f).toInt().coerceAtLeast(12)
        val sonuc = ArrayList<Pair<String, Rect>>()
        var oncekiUst = satirlar.last().second.top
        for (i in satirlar.indices.reversed()) {
            val satir = satirlar[i]
            if (sonuc.isNotEmpty() && oncekiUst - satir.second.top > boslukEsigi) break
            sonuc.add(satir)
            oncekiUst = satir.second.top
            if (sonuc.size >= 3) break
        }
        return sonuc.reversed()
    }

    // ------------------------------------------------------------------ ayıklama

    private fun temizle(raw: String): String {
        var t = raw.replace('\n', ' ').replace('|', 'I')
        t = t.replace(Regex("[\\[\\]{}<>*_]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length < 2) return ""
        val letters = t.count { it.isLetter() }
        if (letters < t.length / 3) return ""
        return t
    }

    /** Altyazı olmayan satırları eler. */
    private fun altyaziDegil(text: String): Boolean {
        val kucuk = text.lowercase()
        for (kalip in BLOCK_PHRASES) if (kucuk.contains(kalip)) return true

        val kelimeler = text.split(Regex("\\s+")).filter { it.isNotBlank() }

        val sikisik = kucuk.filter { !it.isWhitespace() }
        if (sikisik.contains("en•tr") || sikisik.contains("tr•en")) return true

        // Dosya adı, boyut, süre: telefonun kendi arayüzü.
        if (Regex("\\.(zip|apk|rar|pdf|mp4|png|jpg)\\b").containsMatchIn(kucuk)) return true
        if (Regex("\\d+[.,]?\\d*\\s?(mb|kb|gb)\\b").containsMatchIn(kucuk)) return true
        if (Regex("\\d+[.,]?\\d*\\s?(mn|sa|dk|sn|hr|min)\\b").findAll(kucuk).count() >= 2) return true
        if (Regex("\\d{1,2}:\\d{2}").containsMatchIn(text)) return true

        // Anahtar/adres parçası: uzun, harf ve rakam karışık.
        for (k in kelimeler) {
            if (k.length < 16) continue
            if (k.any { it.isDigit() } && k.any { it.isLetter() }) return true
        }

        // Parçalanmış okuma: kırıntı bol. Gerçek kısa kelimeler kırıntı sayılmaz.
        if (kelimeler.size >= 3) {
            val kirinti = kelimeler.count { k ->
                val sade = k.lowercase().trim('.', ',', '!', '?', ':', ';', '"', '\'', '’', '-', '…')
                sade.length <= 2 && sade !in KISA_KELIMELER
            }
            if (kirinti >= 3 && kirinti * 2 >= kelimeler.size) return true
        }

        if (kelimeler.size < 5) return false

        // Cümle kuran kelime varsa repliktir.
        for (k in kelimeler) {
            val sade = k.lowercase().trim('.', ',', '!', '?', ':', ';', '"', '\'')
            if (sade in SENTENCE_WORDS) return false
        }
        if (text.count { it == '.' || it == '!' || it == '?' } >= 2) return false

        // Jenerik listesi: baş harfi büyük özel adlar.
        val ozelAd = kelimeler.count { k ->
            k.length > 1 && k[0].isUpperCase() &&
                    k.drop(1).all { it.isLowerCase() || it == '.' || it == '-' }
        }
        return ozelAd >= 5
    }

    /** Tuş uyarısı, HUD çubuğu, sayaç gibi arayüz yazılarını eler. */
    private fun arayuzYazisi(metin: String): Boolean {
        val s = metin.trim()
        if (s.isEmpty()) return true
        // Noktalamayla biten satır repliktir.
        if (s.last() in ".!?…,:;\"'’") return false
        if (s.startsWith("[") || s.startsWith("(")) return false
        if (TUS_UYARISI.containsMatchIn(s)) return true
        if (TEK_HARF_SIMGE.findAll(s).count() >= 2) return true
        val kelimeler = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (kelimeler.size <= 2) return true
        val harfler = s.filter { it.isLetter() }
        if (kelimeler.size <= 4 && harfler.isNotEmpty() && harfler.all { it.isUpperCase() }) return true
        if (s.trim().uppercase() in UI_WORDS) return true
        return false
    }

    /** İki okuma arasındaki benzerlik (0..1); aynı satırın tekrar çevrilmesini engeller. */
    fun similar(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        val short = if (x.length < y.length) x else y
        val long = if (x.length < y.length) y else x
        if (long.contains(short) && short.length > long.length * 0.8) return 0.95f
        var same = 0
        val n = minOf(x.length, y.length)
        for (i in 0 until n) if (x[i] == y[i]) same++
        return same.toFloat() / maxOf(x.length, y.length)
    }
}
