package com.tinycrap.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** A Material You colour preset: a seed colour plus the full light and dark schemes generated from it. */
class ColorPreset(
    val id: String,
    val name: String,
    val seed: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

object ColorPresets {
    val all: List<ColorPreset> by lazy {
        PresetData.all.map { raw ->
            ColorPreset(raw.id, raw.name, Color(raw.seed), scheme(raw.light, dark = false), scheme(raw.dark, dark = true))
        }
    }

    fun byId(id: String): ColorPreset = all.firstOrNull { it.id == id } ?: all.first()

    // Order matches the generator (see PresetData.kt).
    private fun scheme(v: LongArray, dark: Boolean): ColorScheme {
        fun c(i: Int) = Color(v[i])
        return ColorScheme(
            primary = c(0), onPrimary = c(1), primaryContainer = c(2), onPrimaryContainer = c(3),
            inversePrimary = c(4),
            secondary = c(5), onSecondary = c(6), secondaryContainer = c(7), onSecondaryContainer = c(8),
            tertiary = c(9), onTertiary = c(10), tertiaryContainer = c(11), onTertiaryContainer = c(12),
            background = c(13), onBackground = c(14),
            surface = c(15), onSurface = c(16),
            surfaceVariant = c(17), onSurfaceVariant = c(18),
            surfaceTint = c(0),
            inverseSurface = c(19), inverseOnSurface = c(20),
            error = c(21), onError = c(22), errorContainer = c(23), onErrorContainer = c(24),
            outline = c(25), outlineVariant = c(26),
            scrim = Color.Black,
            surfaceBright = c(27), surfaceDim = c(28),
            surfaceContainerLowest = c(29), surfaceContainerLow = c(30), surfaceContainer = c(31),
            surfaceContainerHigh = c(32), surfaceContainerHighest = c(33),
            primaryFixed = c(2), primaryFixedDim = c(4), onPrimaryFixed = c(3), onPrimaryFixedVariant = c(0),
            secondaryFixed = c(7), secondaryFixedDim = c(5), onSecondaryFixed = c(8), onSecondaryFixedVariant = c(5),
            tertiaryFixed = c(11), tertiaryFixedDim = c(9), onTertiaryFixed = c(12), onTertiaryFixedVariant = c(9),
        )
    }
}
