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
    /** Bulunan altyazının ekrandaki üst kenarı (piksel). 0 ise bilinmiyor. */
    @Volatile var anchorTop = 0
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

    /** Çeviri, okunan alanın hemen üstünde dursun; oyunun kendi altyazısını kapatmasın. */
    private fun bottomPx(): Int {
        val dm = ctx.resources.displayMetrics
        val h = dm.heightPixels
        if (!prefs.alignAbove) return (h * prefs.bottomPct / 100f).toInt()
        val top = if (anchorTop > 0) anchorTop.toFloat() else {
            val land = dm.widthPixels > h
            h * prefs.regionT(land)
        }
        return ((h - top) + h * 0.012f).toInt().coerceIn(0, (h * 0.85f).toInt())
    }

    /** Alan değişince katmanın yeri de güncellensin. */
    fun reposition() {
        val v = root ?: return
        try {
            val lp = v.layoutParams as WindowManager.LayoutParams
            lp.y = bottomPx()
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) { }
    }

    private fun applyStyle() {
        tvTr?.textSize = prefs.textSize.toFloat()
        tvEn?.textSize = (prefs.textSize * 0.62f)
        tvEn?.visibility = if (prefs.showEn) View.VISIBLE else View.GONE
    }

    /** Ana iş parçacığından çağrılır. */
    fun set(tr: String, en: String) {
        applyStyle()
        reposition()
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
