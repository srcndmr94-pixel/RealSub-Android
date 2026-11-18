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
        get() = p.getBoolean("showEn", false)
        set(v) = p.edit().putBoolean("showEn", v).apply()

    /**
     * Altyazı alanı, ekranın oranı olarak (0..1).
     * Dikey ve yatay için ayrı tutulur: telefon yatayken altyazı başka yerde çıkar.
     */
    private fun k(land: Boolean, n: String) = (if (land) "L" else "P") + n

    fun regionL(land: Boolean): Float = p.getFloat(k(land, "rl"), if (land) 0.10f else 0.05f)
    fun regionT(land: Boolean): Float = p.getFloat(k(land, "rt"), if (land) 0.76f else 0.72f)
    fun regionR(land: Boolean): Float = p.getFloat(k(land, "rr"), if (land) 0.90f else 0.95f)
    fun regionB(land: Boolean): Float = p.getFloat(k(land, "rb"), if (land) 0.97f else 0.95f)
    fun regionSet(land: Boolean): Boolean = p.getBoolean(k(land, "set"), false)

    /** Altyazıyı program kendisi bulsun (elle alan seçmeye gerek kalmaz). */
    var autoRegion: Boolean
        get() = p.getBoolean("autoRegion", true)
        set(v) = p.edit().putBoolean("autoRegion", v).apply()

    /** Otomatik kipte ekranın nereden aşağısı taranır (0.45 = alt %55). */
    var scanFrom: Float
        get() = p.getFloat("scanFrom", 0.45f)
        set(v) = p.edit().putFloat("scanFrom", v.coerceIn(0f, 0.8f)).apply()

    /** Çeviriyi, okunan alanın hemen üstüne yerleştir (İngilizcenin üstünde dursun). */
    var alignAbove: Boolean
        get() = p.getBoolean("alignAbove", true)
        set(v) = p.edit().putBoolean("alignAbove", v).apply()

    fun setRegion(land: Boolean, l: Float, t: Float, r: Float, b: Float) {
        p.edit()
            .putFloat(k(land, "rl"), l).putFloat(k(land, "rt"), t)
            .putFloat(k(land, "rr"), r).putFloat(k(land, "rb"), b)
            .putBoolean(k(land, "set"), true)
            .apply()
    }
}
