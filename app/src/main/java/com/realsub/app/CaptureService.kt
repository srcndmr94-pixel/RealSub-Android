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
            workHandler?.postDelayed(this, 650)
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
            if (d > 8) { changed = true; quiet = 0; changes++ } else if (changed) quiet++
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
            val text = Ocr.read(crop)
            reads++
            if (text.isEmpty()) { lastOcr = "(okunamadı)"; return }
            lastOcr = text
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

    private fun translate(text: String): String {
        return try {
            if (prefs.apiKey.isNotEmpty()) Translator.ai(text, prevText, prefs)
            else Translator.free(text, prefs.hard)
        } catch (e: Exception) {
            val m = e.message ?: ""
            failUntil = System.currentTimeMillis() + if (m.contains("429")) 6000 else 2500
            lastError = m.take(160)
            main.post { toast("Çeviri hatası: " + m.take(90)) }
            ""
        }
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

        val l = (sw * prefs.regionL).toInt().coerceIn(0, sw - 2)
        val t = (sh * prefs.regionT).toInt().coerceIn(0, sh - 2)
        val r = (sw * prefs.regionR).toInt().coerceIn(l + 2, sw)
        val b = (sh * prefs.regionB).toInt().coerceIn(t + 2, sh)
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
