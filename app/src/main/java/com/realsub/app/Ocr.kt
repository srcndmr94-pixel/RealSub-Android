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

    /** Arka plan iş parçacığından çağrılır; sonucu bekler. */
    fun read(bmp: Bitmap): String {
        val latch = CountDownLatch(1)
        var out = ""
        try {
            client.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { r -> out = clean(r.text); latch.countDown() }
                .addOnFailureListener { latch.countDown() }
        } catch (e: Exception) {
            return ""
        }
        latch.await(6, TimeUnit.SECONDS)
        return out
    }

    private fun clean(raw: String): String {
        var t = raw.replace('\n', ' ').replace('|', 'I')
        t = t.replace(Regex("[\\[\\]{}<>*_]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        // tek harf veya anlamsız kırıntıları ele
        if (t.length < 2) return ""
        val letters = t.count { it.isLetter() }
        if (letters < t.length / 3) return ""
        return t
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
}
