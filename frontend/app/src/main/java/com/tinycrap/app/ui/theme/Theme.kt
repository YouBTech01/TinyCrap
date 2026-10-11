package com.tinycrap.app.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.tinycrap.app.data.SettingsState
import com.tinycrap.app.data.ThemeMode

/** Medium-sized, softly rounded shapes. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography: Typography = Typography()

@Composable
fun isDarkTheme(settings: SettingsState): Boolean = when (settings.theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun TinyCrapTheme(settings: SettingsState, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Built once per choice (not on every recomposition): dynamic colours are read from the system, which is not free.
    val scheme: ColorScheme = androidx.compose.runtime.remember(dark, useDynamic, settings.preset, settings.amoled) {
        var base = when {
            useDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> ColorPresets.byId(settings.preset).let { if (dark) it.dark else it.light }
        }
        if (dark && settings.amoled) base = base.amoled()
        base
    }

    // Keep status/navigation bar icons readable when the app theme differs from the system theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = animatedScheme(scheme),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** True black surfaces for OLED screens; accent colours stay the same. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceBright = Color(0xFF2A2A2A),
)

/** Cross-fades every colour when the theme, preset or dark mode changes. Costs nothing while the theme stays the same. */
@Composable
private fun animatedScheme(t: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 380)
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, spec, label = "scheme").value
    return t.copy(
        primary = a(t.primary), onPrimary = a(t.onPrimary),
        primaryContainer = a(t.primaryContainer), onPrimaryContainer = a(t.onPrimaryContainer),
        inversePrimary = a(t.inversePrimary),
        secondary = a(t.secondary), onSecondary = a(t.onSecondary),
        secondaryContainer = a(t.secondaryContainer), onSecondaryContainer = a(t.onSecondaryContainer),
        tertiary = a(t.tertiary), onTertiary = a(t.onTertiary),
        tertiaryContainer = a(t.tertiaryContainer), onTertiaryContainer = a(t.onTertiaryContainer),
        background = a(t.background), onBackground = a(t.onBackground),
        surface = a(t.surface), onSurface = a(t.onSurface),
        surfaceVariant = a(t.surfaceVariant), onSurfaceVariant = a(t.onSurfaceVariant),
        surfaceTint = a(t.surfaceTint),
        inverseSurface = a(t.inverseSurface), inverseOnSurface = a(t.inverseOnSurface),
        error = a(t.error), onError = a(t.onError),
        errorContainer = a(t.errorContainer), onErrorContainer = a(t.onErrorContainer),
        outline = a(t.outline), outlineVariant = a(t.outlineVariant),
        surfaceBright = a(t.surfaceBright), surfaceDim = a(t.surfaceDim),
        surfaceContainerLowest = a(t.surfaceContainerLowest), surfaceContainerLow = a(t.surfaceContainerLow),
        surfaceContainer = a(t.surfaceContainer), surfaceContainerHigh = a(t.surfaceContainerHigh),
        surfaceContainerHighest = a(t.surfaceContainerHighest),
    )
}
