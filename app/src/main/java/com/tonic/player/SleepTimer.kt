package com.tonic.player

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/** Таймер сна: живёт в процессе сервиса, поэтому работает и при закрытом экране. */
object SleepTimer {
    var endsAt by mutableLongStateOf(0L)
        private set

    @Volatile var player: Player? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable {
        player?.pause()
        endsAt = 0L
    }

    fun set(minutes: Int) {
        handler.removeCallbacks(fire)
        if (minutes <= 0) {
            endsAt = 0L
            return
        }
        endsAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(fire, minutes * 60_000L)
    }

    fun minutesLeft(): Int = ((endsAt - System.currentTimeMillis()) / 60_000L + 1).toInt().coerceAtLeast(0)
}
