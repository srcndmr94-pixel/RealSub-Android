package com.realsub.app

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

/** Altyazının ekranda çıktığı yeri parmakla çerçeveleme ekranı. */
class RegionActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        setContentView(PickView(this))
    }

    inner class PickView(c: android.content.Context) : View(c) {

        private val dim = Paint().apply { color = Color.parseColor("#99000000") }
        private val clear = Paint().apply { color = Color.TRANSPARENT; xfermode =
            android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
        private val border = Paint().apply {
            color = Color.parseColor("#7C6CFF"); style = Paint.Style.STROKE; strokeWidth = 5f
        }
        private val label = Paint().apply { color = Color.WHITE; textSize = 40f; isAntiAlias = true }
        private val hint = Paint().apply { color = Color.parseColor("#CCD3E6"); textSize = 34f; isAntiAlias = true }

        private var r = RectF()
        private var x0 = 0f
        private var y0 = 0f
        private var drawn = false

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            post {
                r = RectF(width * prefs.regionL, height * prefs.regionT,
                    width * prefs.regionR, height * prefs.regionB)
                drawn = prefs.regionSet
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
            if (drawn) {
                canvas.drawRect(r, clear)
                canvas.drawRect(r, border)
            }
            canvas.drawText("Altyazının çıktığı yeri parmağınla çerçevele", 40f, 120f, label)
            canvas.drawText("Bitince parmağını kaldır; alan kaydedilir.", 40f, 170f, hint)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.x; y0 = e.y
                    r = RectF(x0, y0, x0, y0)
                    drawn = true
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    r = RectF(minOf(x0, e.x), minOf(y0, e.y), maxOf(x0, e.x), maxOf(y0, e.y))
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (r.width() > 40 && r.height() > 25) {
                        prefs.setRegion(
                            (r.left / width).coerceIn(0f, 1f),
                            (r.top / height).coerceIn(0f, 1f),
                            (r.right / width).coerceIn(0f, 1f),
                            (r.bottom / height).coerceIn(0f, 1f)
                        )
                        Toast.makeText(context, "Alan kaydedildi", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(context, "Çok küçük — tekrar dene", Toast.LENGTH_SHORT).show()
                        drawn = false
                        invalidate()
                    }
                }
            }
            return true
        }
    }
}
