package com.realsub.app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Cihaz içi metin okuma. İnternet gerekmez, ücretsizdir. */
object Ocr {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Bulunan altyazı: metni ve tarama görüntüsündeki dikey yeri. */
    class Found(val text: String, val top: Int, val bottom: Int)

    /**
     * Tarama görüntüsünü okur ve içindeki altyazıyı kendisi bulur.
     * Arayüz yazıları, süre göstergeleri ve kenarda kalan küçük yazılar elenir.
     * Arka plan iş parçacığından çağrılır; sonucu bekler.
     */
    fun detect(bmp: Bitmap, auto: Boolean, band: Float = -1f, bandTol: Float = 0f): Found? {
        val latch = CountDownLatch(1)
        var lines: List<Pair<String, android.graphics.Rect>> = emptyList()
        try {
            client.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { r ->
                    val acc = ArrayList<Pair<String, android.graphics.Rect>>()
                    for (block in r.textBlocks) for (ln in block.lines) {
                        val box = ln.boundingBox ?: continue
                        acc.add(ln.text to box)
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

        // 1) arayüz yazılarını ve kırıntıları ele
        val ok = lines.filter { (t, box) ->
            val txt = clean(t)
            txt.isNotEmpty() &&
                    box.height() >= maxOf(9, (h * 0.035f).toInt()) &&
                    box.width() >= w * 0.05f
        }
        if (ok.isEmpty()) return null

        if (!auto) {
            val sorted = ok.sortedBy { it.second.top }
            val text = clean(sorted.joinToString(" ") { it.first })
            if (text.isEmpty()) return null
            return Found(text, sorted.first().second.top, sorted.last().second.bottom)
        }

        // 2) ortaya yakın duranlar altyazıdır; kenarda kalanlar arayüzdür
        val cx = w / 2f
        val central = ok.filter { (_, b) ->
            Math.abs((b.left + b.right) / 2f - cx) < w * 0.42f
        }
        var pool = if (central.isNotEmpty()) central else ok

        // 3) altyazının bulunduğu şerit biliniyorsa dışındakilere bakılmaz;
        //    oynatıcı arayüzü başka yükseklikte çıktığı için böylece elenir
        if (band >= 0f) {
            val inBand = pool.filter { (_, b) -> Math.abs((b.top + b.bottom) / 2f - band) <= bandTol }
            if (inBand.isEmpty()) return null
            pool = inBand
        }

        // 4) en alttaki satır çekirdek alınır, yakınındakiler onunla birleştirilir
        val seed = pool.maxByOrNull { it.second.bottom } ?: return null
        val sh = seed.second.height().toFloat()
        val group = pool.filter { (_, b) ->
            Math.abs(((b.top + b.bottom) / 2f) - ((seed.second.top + seed.second.bottom) / 2f)) < sh * 2.6f
        }.sortedBy { it.second.top }

        val text = clean(group.joinToString(" ") { it.first })
        if (text.isEmpty()) return null
        return Found(text, group.first().second.top, group.last().second.bottom)
    }

    /** İki okuma arasındaki benzerlik (0..1). Aynı satırın tekrar çevrilmesini engeller. */
    fun similar(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val x = a.lowercase().filter { it.isLetterOrDigit() }
        val y = b.lowercase().filter { it.isLetterOrDigit() }
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

    private fun clean(raw: String): String {
        var t = raw.replace('\n', ' ').replace('|', 'I')
        t = t.replace(Regex("[\\[\\]{}<>*_]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length < 2) return ""
        val letters = t.count { it.isLetter() }
        if (letters < t.length / 3) return ""
        if (looksLikeUi(t)) return ""
        return t
    }

    /**
     * Oynatıcı arayüzü altyazı değildir. Kontroller açıldığında aynı şeride
     * süre, düğme ve öneri yazıları girer; bunlar elenir.
     */
    private val UI_PHRASES = arrayOf(
        "ürünleri göster", "diğer videolar", "abone ol", "otomatik oynat", "daha sonra izle",
        "oynatma listesi", "izlenme", "yorumlar", "paylaş", "beğen", "kaydet",
        "watch later", "up next", "show products", "subscribe", "autoplay", "shorts",
        "sonraki video", "canlı yayın", "tam ekran", "video kalitesi"
    )

    private fun looksLikeUi(t: String): Boolean {
        if (t.length > 170) return true
        val low = t.lowercase()
        for (u in UI_PHRASES) if (low.contains(u)) return true
        if (Regex("\\d{1,2}:\\d{2}").containsMatchIn(t)) return true
        val digits = t.count { it.isDigit() }
        if (digits > t.length / 4) return true
        val symbols = t.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ",.!?'\u2019-" }
        if (symbols > 3) return true
        if (gibberish(t)) return true
        return false
    }

    /** Kısa, büyük harfli, sesli harfsiz kırıntılar arayüzden sızan yazılardır: "XRO G" gibi. */
    private val COMMON = setOf(
        "the", "and", "you", "but", "for", "not", "now", "get", "got", "out", "man", "hey", "yes",
        "yeah", "no", "ok", "okay", "why", "who", "how", "all", "let", "she", "him", "her", "his",
        "was", "are", "can", "did", "kid", "run", "gun", "one", "two", "try", "say", "see", "sir",
        "yo", "hi", "oh", "ah", "eh", "uh", "mr", "ms", "dr", "god", "damn", "hell", "shit", "fuck",
        "ass", "way", "day", "job", "car", "cop", "boy", "dad", "mom", "bro", "dog", "off", "put",
        "set", "top", "bad", "big", "old", "new", "hot", "far", "low", "up", "in", "on", "at", "to",
        "it", "is", "be", "do", "go", "we", "he", "me", "my", "so", "if", "as", "or", "an", "a", "i"
    )

    private fun gibberish(t: String): Boolean {
        val tokens = t.split(Regex("\\s+")).filter { tk -> tk.any { it.isLetter() } }
        if (tokens.size < 2) return false
        val vowels = "aeiouyàáâäéèêëíìîïóòôöúùûüıöü"
        var bad = 0
        for (tk in tokens) {
            val w = tk.filter { it.isLetter() }
            val lw = w.lowercase()
            if (w.length == 1 && lw != "a" && lw != "i" && lw != "o") { bad++; continue }
            if (w.length >= 2 && w.none { vowels.contains(it.lowercaseChar()) }) { bad++; continue }
            if (w.length <= 3 && w == w.uppercase() && !COMMON.contains(lw)) bad++
        }
        return bad * 100 / tokens.size >= 40
    }
}
