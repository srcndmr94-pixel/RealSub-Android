package com.realsub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast

/**
 * Ekranı kaydeder, seçili alandaki altyazıyı okur, çevirir ve katmanda gösterir.
 * İstek yalnızca altyazı değiştiğinde gönderilir.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_TEST = "test"
        private const val CHANNEL = "realsub"
        private const val NOTIF_ID = 41
        @Volatile var running = false

        /** Durum ekranı için sayaçlar — aynı süreçte olduğumuz için doğrudan okunur. */
        @Volatile var frames = 0
        @Volatile var changes = 0
        @Volatile var reads = 0
        @Volatile var translations = 0
        @Volatile var lastOcr = ""
        @Volatile var lastTr = ""
        @Volatile var lastError = ""
        @Volatile var lastBright = 0
        @Volatile var forceOnce = false

        fun resetStats() {
            frames = 0; changes = 0; reads = 0; translations = 0
            lastOcr = ""; lastTr = ""; lastError = ""; lastBright = 0
        }
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private lateinit var prefs: Prefs
    private var overlay: Overlay? = null

    private var worker: HandlerThread? = null
    private var workHandler: Handler? = null
    private val main = Handler(Looper.getMainLooper())

    private var sw = 0
    private var sh = 0
    private var lastSig: IntArray? = null
    private var changed = false
    private var quiet = 0
    private var lastText = ""
    private var prevText = ""
    private var busy = false
    private var failUntil = 0L
    private var lastToastAt = 0L
    private var cropTop = 0
    private var missCount = 0
    /** Altyazının bulunduğu şerit (kırpılmış görüntüdeki dikey orta nokta). */
    private var band = -1f
    private var bandMiss = 0
    private var lastCropH = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = Prefs(this)

        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TEST) {
            forceOnce = true
            return START_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)

        if (code == 0 || data == null) {
            toast("Ekran izni alınamadı.")
            stopEverything()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = try {
            mpm.getMediaProjection(code, data)
        } catch (e: Exception) {
            null
        }
        if (p == null) {
            toast("Ekran kaydı başlatılamadı.")
            stopEverything()
            return START_NOT_STICKY
        }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopEverything() }
            }
        }, main)

        resetStats()
        band = -1f; bandMiss = 0; lastCropH = 0
        startCapture()
        running = true
        return START_STICKY
    }

    // ------------------------------------------------------------ ekran kaydı
    private fun startCapture() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)

        // Tam çözünürlük: altyazı yazısı küçük olduğu için okuma doğruluğu buna bağlı.
        val scale = 1
        sw = dm.widthPixels
        sh = dm.heightPixels

        reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay(
            "RealSub", sw, sh, dm.densityDpi / scale,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, null
        )

        overlay = Overlay(this, prefs).also { main.post { it.show() } }

        worker = HandlerThread("realsub-loop").also { it.start() }
        workHandler = Handler(worker!!.looper)
        workHandler?.post(loop)
    }

    private val loop = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) { }
            workHandler?.postDelayed(this, 450)
        }
    }

    private fun tick() {
        if (busy || System.currentTimeMillis() < failUntil) return
        val img = reader?.acquireLatestImage() ?: return
        val crop: Bitmap? = try { cropRegion(img) } finally { img.close() }
        if (crop == null) return

        frames++
        val sig = signature(crop)
        lastBright = sig[sig.size - 1]
        val blank = lastBright < (sig.size - 1) * 0.0015   // yazı yok
        val prev = lastSig
        if (prev != null) {
            val d = diff(sig, prev)
            // Otomatik kipte geniş alan taranır; oyundaki ışık/patlama boşuna tetiklemesin.
            val esik = if (prefs.autoRegion) 14 else 8
            if (d > esik) { changed = true; quiet = 0; changes++ } else if (changed) quiet++
        }
        lastSig = sig

        val force = forceOnce
        if (force) forceOnce = false

        if (blank && !force) {
            if (changed && quiet >= 1) {
                changed = false
                lastText = ""
                post("", "")
            }
            crop.recycle()
            return
        }
        if (!force && (!changed || quiet < 1)) { crop.recycle(); return }
        changed = false

        busy = true
        try {
            if (crop.height != lastCropH) { lastCropH = crop.height; band = -1f }
            val tol = crop.height * 0.13f
            var found = Ocr.detect(crop, prefs.autoRegion, band, tol)
            if (found == null && band >= 0f) {
                // Şeritte bir şey yok: birkaç denemeden sonra şerit serbest bırakılır.
                bandMiss++
                if (bandMiss >= 5) { band = -1f; bandMiss = 0; found = Ocr.detect(crop, prefs.autoRegion) }
            }
            reads++
            if (found == null) {
                lastOcr = "(altyazı bulunamadı)"
                missCount++
                if (missCount >= 4) { lastText = ""; post("", "") }
                return
            }
            missCount = 0
            bandMiss = 0
            val text = found.text
            lastOcr = text
            // Altyazının şeridi öğrenilir; sonraki karelerde arayüz yazıları elenir.
            band = if (band < 0f) (found.top + found.bottom) / 2f
                   else band * 0.75f + ((found.top + found.bottom) / 2f) * 0.25f
            // Bulunan altyazının ekrandaki yeri: çeviri bunun üstüne yerleşir.
            overlay?.anchorTop = cropTop + found.top
            if (!force && Ocr.similar(text, lastText) > 0.85f) return
            lastText = text
            val tr = translate(text)
            if (tr.isNotEmpty()) {
                translations++
                lastTr = tr
                prevText = text
                post(tr, text)
            } else {
                // Çeviri olmasa bile okunanı göster: nerede takıldığı anlaşılsın.
                post("", text)
            }
        } finally {
            busy = false
            crop.recycle()
        }
    }

    /**
     * Önce yapay zekâ denenir. Model meşgulse bir kez daha denenir;
     * yine olmazsa ücretsiz motora düşülür ki ekran boş kalmasın.
     */
    private fun translate(text: String): String {
        val key = prefs.apiKey
        if (key.isNotEmpty()) {
            var attempt = 0
            while (attempt < 2) {
                try {
                    val r = Translator.ai(text, prevText, prefs)
                    if (r.isNotEmpty()) { lastError = ""; return r }
                    break
                } catch (e: Exception) {
                    val m = e.message ?: ""
                    lastError = m.take(160)
                    val busyModel = m.contains("high demand", true) || m.contains("overload", true) ||
                            m.contains("503") || m.contains("UNAVAILABLE", true)
                    val quota = m.contains("429") || m.contains("quota", true) ||
                            m.contains("RESOURCE_EXHAUSTED", true)
                    if (busyModel && attempt == 0) {
                        attempt++
                        try { Thread.sleep(800) } catch (ie: InterruptedException) { }
                        continue
                    }
                    failUntil = System.currentTimeMillis() + if (quota) 10000 else 1500
                    noteError(if (quota) "Kota doldu, bekleniyor" else "Model meşgul, ücretsiz motora düşüldü")
                    break
                }
            }
        }
        return try {
            Translator.free(text, prefs.hard)
        } catch (e2: Exception) {
            lastError = (e2.message ?: "çeviri yok").take(160)
            noteError("Çeviri yapılamadı")
            ""
        }
    }

    /** Uyarıyı en çok 12 saniyede bir gösterir; ekran uyarıya boğulmasın. */
    private fun noteError(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastAt < 12000) return
        lastToastAt = now
        main.post { toast(msg) }
    }

    private fun post(tr: String, en: String) {
        main.post { overlay?.set(tr, en) }
    }

    // ------------------------------------------------------------ görüntü işleme
    private fun cropRegion(img: Image): Bitmap? {
        val plane = img.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPad = rowStride - pixelStride * img.width
        val full = Bitmap.createBitmap(img.width + rowPad / pixelStride, img.height, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)

        val land = sw > sh
        val auto = prefs.autoRegion
        val l = if (auto) 0 else (sw * prefs.regionL(land)).toInt().coerceIn(0, sw - 2)
        val t = if (auto) (sh * prefs.scanFrom).toInt().coerceIn(0, sh - 2)
                else (sh * prefs.regionT(land)).toInt().coerceIn(0, sh - 2)
        val r = if (auto) sw else (sw * prefs.regionR(land)).toInt().coerceIn(l + 2, sw)
        val b = if (auto) sh else (sh * prefs.regionB(land)).toInt().coerceIn(t + 2, sh)
        cropTop = t
        return try {
            val out = Bitmap.createBitmap(full, l, t, r - l, b - t)
            if (out != full) full.recycle()
            out
        } catch (e: Exception) {
            full.recycle()
            null
        }
    }

    /** Küçültülmüş parlaklık haritası; son hücre parlak piksel sayısını tutar. */
    private fun signature(bmp: Bitmap): IntArray {
        val w = 96
        val h = 26
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        val out = IntArray(w * h + 1)
        var bright = 0
        for (i in px.indices) {
            val c = px[i]
            val g = ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10
            out[i] = g
            if (g > 150) bright++
        }
        out[out.size - 1] = bright
        return out
    }

    private fun diff(a: IntArray, b: IntArray): Int {
        var d = 0
        for (i in 0 until a.size - 1) {
            if ((a[i] > 130 || b[i] > 130) && Math.abs(a[i] - b[i]) > 45) d++
        }
        return d
    }

    // ------------------------------------------------------------ bildirim / kapanış
    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "RealSub", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val stop = Intent(this, CaptureService::class.java).setAction(ACTION_STOP)
        val flag = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getService(this, 1, stop, flag)

        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("RealSub çalışıyor")
            .setContentText("Altyazı okunuyor — durdurmak için dokun")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun toast(s: String) {
        Toast.makeText(applicationContext, s, Toast.LENGTH_SHORT).show()
    }

    private fun stopEverything() {
        running = false
        try { workHandler?.removeCallbacksAndMessages(null) } catch (e: Exception) { }
        try { worker?.quitSafely() } catch (e: Exception) { }
        worker = null
        workHandler = null
        main.post { overlay?.hide() }
        try { display?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        try { projection?.stop() } catch (e: Exception) { }
        display = null
        reader = null
        projection = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
