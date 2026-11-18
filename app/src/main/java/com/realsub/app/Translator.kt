package com.realsub.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Çeviri. Ağ işi olduğu için yalnızca arka plan iş parçacığından çağrılır. */
object Translator {

    private val HARD_RULES = arrayOf(
        "Küfürleri yumuşat, argoyu sadeleştir; aile ortamında izlenebilir bir dil kullan.",
        "Doğal konuşma Türkçesi kullan. Küfürleri ölçülü ama gerçek karşılığıyla ver, tamamen silme.",
        "SOKAK AĞZI kullan. Küfür, argo ve hakaret neyse odur: yumuşatma, kibarlaştırma, sansürleme. 'fuck' kelimesini 'kahretsin' diye geçiştirme; bağlama göre gerçek karşılığını yaz.",
        "HİÇBİR ŞEYİ YUMUŞATMA. En sert, en doğrudan Türkçe karşılığı kullan. Küfür tam karşılanır, hiçbir kelime yıldızlanmaz."
    )

    private fun systemPrompt(hard: Int): String = buildString {
        append("Sen deneyimli bir altyazı çevirmenisin. Ekrandan okunmuş İngilizce altyazıyı Türkçeye çeviriyorsun.\n\n")
        append("KURALLAR:\n")
        append("1. ").append(HARD_RULES[hard.coerceIn(0, 3)]).append("\n")
        append("2. Kelimesi kelimesine değil; o repliği Türkçe konuşan biri nasıl söylerdi, öyle çevir.\n")
        append("3. Metin ekrandan okunduğu için harf hatası olabilir; anlamı tamamla, hatayı düzelt.\n")
        append("4. Altyazı kısalığına uy: en çok iki satır.\n")
        append("5. Özel isimler ve oyun terimleri çevrilmez.\n")
        append("6. Açıklama, not veya tırnak ekleme.\n\n")
        append("ÇIKTI: sadece JSON. {\"tr\":\"çeviri\"}")
    }

    /** Yapay zekâ çevirisi (Gemini). Başarısız olursa özel durum fırlatır. */
    fun ai(text: String, prev: String, prefs: Prefs): String {
        val key = prefs.apiKey
        require(key.isNotEmpty()) { "Anahtar yok" }
        val hints = Slang.hintsFor(text, prefs.hard)
        val user = buildString {
            if (prev.isNotEmpty()) append("ÖNCEKİ REPLİK (bağlam, çevirme): ").append(prev).append("\n\n")
            append("ÇEVİRİLECEK: ").append(text)
            if (hints.isNotEmpty()) append("\n\nÖNERİLEN KARŞILIKLAR: ").append(hints.joinToString(" ; "))
            append("\n\nSadece JSON döndür.")
        }

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt(prefs.hard)))))
            put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", user)))))
            put("generationConfig", JSONObject()
                .put("temperature", 0.7)
                .put("responseMimeType", "application/json")
                .put("maxOutputTokens", 400))
            val safety = JSONArray()
            for (c in arrayOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")) {
                safety.put(JSONObject().put("category", c).put("threshold", "BLOCK_NONE"))
            }
            put("safetySettings", safety)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                prefs.model + ":generateContent?key=" + URLEncoder.encode(key, "UTF-8")
        val raw = post(url, body.toString())
        val j = JSONObject(raw)
        if (j.has("error")) throw RuntimeException(j.getJSONObject("error").optString("message", "Çeviri hatası"))
        val cands = j.optJSONArray("candidates") ?: throw RuntimeException("Yanıt boş")
        if (cands.length() == 0) throw RuntimeException("Yanıt boş")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw RuntimeException("Yanıt boş")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
        return extractTr(sb.toString())
    }

    private fun extractTr(s: String): String {
        val t = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        try {
            return JSONObject(t).optString("tr", "").trim()
        } catch (e: Exception) {
            val m = Regex("\"tr\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(t)
            if (m != null) return m.groupValues[1].replace("\\\"", "\"").replace("\\n", " ").trim()
        }
        return ""
    }

    /** Anahtar yokken kullanılan ücretsiz motor; argo sözlüğü yine uygulanır. */
    fun free(text: String, hard: Int): String {
        val (masked, map) = Slang.protect(text, hard)
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=tr&dt=t&q=" +
                URLEncoder.encode(masked, "UTF-8")
        val raw = get(url)
        val arr = JSONArray(raw).optJSONArray(0) ?: return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append(arr.getJSONArray(i).optString(0, ""))
        return Slang.restore(sb.toString(), map).trim()
    }

    private fun post(url: String, body: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        OutputStreamWriter(c.outputStream, "UTF-8").use { it.write(body) }
        return readAll(c)
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        return readAll(c)
    }

    private fun readAll(c: HttpURLConnection): String {
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val text = BufferedReader(InputStreamReader(stream ?: return "", "UTF-8")).use { it.readText() }
        c.disconnect()
        return text
    }
}
