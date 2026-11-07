package com.tonic.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class IntPref(private val sp: SharedPreferences, private val key: String, def: Int) {
    var value by mutableIntStateOf(sp.getInt(key, def))
        private set

    fun set(v: Int) {
        value = v
        sp.edit().putInt(key, v).apply()
    }
}

class BoolPref(private val sp: SharedPreferences, private val key: String, def: Boolean) {
    var value by mutableStateOf(sp.getBoolean(key, def))
        private set

    fun set(v: Boolean) {
        value = v
        sp.edit().putBoolean(key, v).apply()
    }
}

/** Настройки оформления и плеера. */
class UiPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val scheme = IntPref(sp, "scheme", 1)        // 0 динамический, 1 индиго (как ViMusic)
    val theme = IntPref(sp, "theme", 0)          // 0 системная, 1 светлая, 2 тёмная
    val black = BoolPref(sp, "black", false)     // чёрный фон
    val radius = IntPref(sp, "radius", 2)        // скруглённость миниатюр 0..3
    val glass = BoolPref(sp, "glass", false)     // «жидкое стекло»
    val miniPrev = BoolPref(sp, "miniPrev", false)
    val lowQuality = BoolPref(sp, "lowq", false)

    fun resetAll() {
        scheme.set(1); theme.set(0); black.set(false); radius.set(2)
        glass.set(false); miniPrev.set(false); lowQuality.set(false)
        Yt.lowQuality = false
    }
}
