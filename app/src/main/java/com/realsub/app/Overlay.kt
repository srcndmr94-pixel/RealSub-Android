package com.realsub.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Her uygulamanın üstünde duran altyazı katmanı. */
class Overlay(private val ctx: Context, private val prefs: Prefs) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var tvTr: TextView? = null
    private var tvEn: TextView? = null

    fun show() {
        if (root != null) return
        val v = LayoutInflater.from(ctx).inflate(R.layout.overlay, null)
        tvTr = v.findViewById(R.id.ovTr)
        tvEn = v.findViewById(R.id.ovEn)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = bottomPx()

        applyStyle()
        try {
            wm.addView(v, lp)
            root = v
        } catch (e: Exception) {
            root = null
        }
    }

    private fun bottomPx(): Int {
        val h = ctx.resources.displayMetrics.heightPixels
        return (h * prefs.bottomPct / 100f).toInt()
    }

    private fun applyStyle() {
        tvTr?.textSize = prefs.textSize.toFloat()
        tvEn?.textSize = (prefs.textSize * 0.62f)
        tvEn?.visibility = if (prefs.showEn) View.VISIBLE else View.GONE
    }

    /** Ana iş parçacığından çağrılır. */
    fun set(tr: String, en: String) {
        applyStyle()
        tvTr?.text = tr
        tvTr?.visibility = if (tr.isEmpty()) View.GONE else View.VISIBLE
        // Çeviri gelmese bile okunan İngilizce gösterilir: nerede takıldığı görünsün.
        val showEn = prefs.showEn && en.isNotEmpty()
        tvEn?.text = if (showEn) en else ""
        tvEn?.visibility = if (showEn) View.VISIBLE else View.GONE
    }

    fun hide() {
        val v = root ?: return
        try { wm.removeView(v) } catch (e: Exception) { }
        root = null
        tvTr = null
        tvEn = null
    }
}
