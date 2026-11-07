package app.tonic

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** mode: 0 — системная, 1 — тёмная, 2 — светлая. На Android 12+ цвета динамические. */
@Composable
fun TonicTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFF9FA8FF),
            background = Color(0xFF121218),
            surface = Color(0xFF121218)
        )
        else -> lightColorScheme(primary = Color(0xFF4A4FD8))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
