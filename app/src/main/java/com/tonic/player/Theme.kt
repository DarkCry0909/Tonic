package com.tonic.player

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage

val LocalPrefs = staticCompositionLocalOf<UiPrefs> { error("UiPrefs not provided") }

@Composable
fun TonicTheme(prefs: UiPrefs, content: @Composable () -> Unit) {
    val dark = when (prefs.theme.value) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    var scheme = when {
        prefs.scheme.value == 0 && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFF5F65D9), onPrimary = Color.White, secondary = Color(0xFFBF4040),
            background = Color(0xFF16171D), onBackground = Color(0xFFE1E1E2),
            surface = Color(0xFF1F2029), onSurface = Color(0xFFE1E1E2),
            surfaceVariant = Color(0xFF2B2D3B), onSurfaceVariant = Color(0xFFA3A4A6),
            surfaceContainer = Color(0xFF1F2029), surfaceContainerHigh = Color(0xFF2B2D3B)
        )
        else -> lightColorScheme(
            primary = Color(0xFF5055C0), onPrimary = Color.White, secondary = Color(0xFFBF4040),
            background = Color(0xFFFDFDFE), onBackground = Color(0xFF212121),
            surface = Color(0xFFF8F8FC), onSurface = Color(0xFF212121),
            surfaceVariant = Color(0xFFEAEAF5), onSurfaceVariant = Color(0xFF656566),
            surfaceContainer = Color(0xFFF8F8FC), surfaceContainerHigh = Color(0xFFEAEAF5)
        )
    }
    if (dark && prefs.black.value) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color(0xFF0B0B0E), surfaceVariant = Color(0xFF1A1A20),
            surfaceContainer = Color(0xFF0B0B0E), surfaceContainerHigh = Color(0xFF1A1A20)
        )
    }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalPrefs provides prefs) {
        MaterialTheme(colorScheme = scheme) {
            Surface(
                Modifier.fillMaxSize(),
                color = scheme.background,
                contentColor = scheme.onBackground,
                content = content
            )
        }
    }
}

// ---------- «Жидкое стекло» (включается в настройках) ----------
fun Modifier.glass(shape: Shape, tint: Float = 0.08f, dark: Float = 0.25f): Modifier = this
    .clip(shape)
    .background(Color.Black.copy(alpha = dark))
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = tint + 0.07f), Color.White.copy(alpha = tint))))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.06f), Color.White.copy(0.28f))),
        shape
    )

/** Панель: плоская (стиль ViMusic) или стеклянная, в зависимости от настройки. */
@Composable
fun Modifier.panel(shape: Shape, flat: Color, tint: Float = 0.08f, dark: Float = 0.25f): Modifier =
    if (LocalPrefs.current.glass.value) this.glass(shape, tint, dark) else this.clip(shape).background(flat)

@Composable
fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().panel(RoundedCornerShape(22.dp), MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
fun GlassBackdrop(vm: AppVm, strong: Boolean) {
    val art = vm.currentTrack?.thumb
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier.fillMaxSize().background(bg).drawBehind {
            val w = size.width
            val c1 = Offset(w * 0.1f, size.height * 0.15f)
            val c2 = Offset(w * 0.95f, size.height * 0.75f)
            drawCircle(Brush.radialGradient(listOf(Color(0x886A3DFF), Color.Transparent), c1, w * 0.9f), w * 0.9f, c1)
            drawCircle(Brush.radialGradient(listOf(Color(0x66FF4F9A), Color.Transparent), c2, w * 0.8f), w * 0.8f, c2)
        }
    ) {
        if (art != null && Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(90.dp).alpha(if (strong) 0.6f else 0.38f)
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (strong) 0.25f else 0.35f)))
    }
}
