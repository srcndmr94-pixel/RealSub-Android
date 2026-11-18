package com.realsub.app

import android.content.Context
import android.content.SharedPreferences

/** Tüm ayarlar burada; başka yerde SharedPreferences açılmaz. */
class Prefs(ctx: Context) {

    private val p: SharedPreferences =
        ctx.getSharedPreferences("realsub", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v.trim()).apply()

    var model: String
        get() = p.getString("model", "gemini-3.8-flash") ?: "gemini-3.8-flash"
        set(v) = p.edit().putString("model", v.trim().ifEmpty { "gemini-3.8-flash" }).apply()

    /** 0 yumuşak · 1 doğal · 2 sokak ağzı · 3 tam sansürsüz */
    var hard: Int
        get() = p.getInt("hard", 2)
        set(v) = p.edit().putInt("hard", v.coerceIn(0, 3)).apply()

    var textSize: Int
        get() = p.getInt("size", 26)
        set(v) = p.edit().putInt("size", v.coerceIn(12, 54)).apply()

    var bottomPct: Int
        get() = p.getInt("bottom", 10)
        set(v) = p.edit().putInt("bottom", v.coerceIn(0, 60)).apply()

    var showEn: Boolean
        get() = p.getBoolean("showEn", true)
        set(v) = p.edit().putBoolean("showEn", v).apply()

    /** Altyazı alanı, ekranın oranı olarak (0..1) */
    var regionL: Float
        get() = p.getFloat("rl", 0.05f)
        set(v) = p.edit().putFloat("rl", v).apply()
    var regionT: Float
        get() = p.getFloat("rt", 0.72f)
        set(v) = p.edit().putFloat("rt", v).apply()
    var regionR: Float
        get() = p.getFloat("rr", 0.95f)
        set(v) = p.edit().putFloat("rr", v).apply()
    var regionB: Float
        get() = p.getFloat("rb", 0.95f)
        set(v) = p.edit().putFloat("rb", v).apply()

    var regionSet: Boolean
        get() = p.getBoolean("regionSet", false)
        set(v) = p.edit().putBoolean("regionSet", v).apply()

    fun setRegion(l: Float, t: Float, r: Float, b: Float) {
        p.edit()
            .putFloat("rl", l).putFloat("rt", t)
            .putFloat("rr", r).putFloat("rb", b)
            .putBoolean("regionSet", true)
            .apply()
    }
}
