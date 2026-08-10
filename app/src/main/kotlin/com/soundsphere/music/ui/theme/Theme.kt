/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

// The auth-screen accent (warm earthy brown). Doubles as the sentinel that
// selects the fixed Earthy palettes instead of dynamic colors.
val DefaultThemeColor = Color(0xFF5E503F)

// The previous default (crimson) that existing installs may still have
// stored; keep mapping it to the Earthy palettes so upgrading doesn't
// silently switch the theme to dynamic colors.
val LegacyDefaultThemeColor = Color(0xFFED5564)

// Earthy Tones design system (dark) — from stitch_earthy_tones_ui_redesign
val EarthyDarkColorScheme = ColorScheme(
    primary = Color(0xFF5E503F),
    onPrimary = Color(0xFFEAE0D5),
    primaryContainer = Color(0xFFEAE0D5),
    onPrimaryContainer = Color(0xFF6A635A),
    inversePrimary = Color(0xFF645D55),
    secondary = Color(0xFFDDC2A4),
    onSecondary = Color(0xFF3E2D18),
    secondaryContainer = Color(0xFF59462F),
    onSecondaryContainer = Color(0xFFCEB497),
    tertiary = Color(0xFFFFFDFF),
    onTertiary = Color(0xFF3A2E1F),
    tertiaryContainer = Color(0xFFF3DEC8),
    onTertiaryContainer = Color(0xFF70614F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141312),
    onBackground = Color(0xFFE6E1E0),
    surface = Color(0xFF141312),
    onSurface = Color(0xFFE6E1E0),
    surfaceVariant = Color(0xFF363433),
    onSurfaceVariant = Color(0xFFCEC5BC),
    surfaceTint = Color(0xFFCEC5BA),
    inverseSurface = Color(0xFFE6E1E0),
    inverseOnSurface = Color(0xFF31302F),
    outline = Color(0xFF979087),
    outlineVariant = Color(0xFF4B463F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3938),
    surfaceDim = Color(0xFF141312),
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1C1B1B),
    surfaceContainer = Color(0xFF201F1F),
    surfaceContainerHigh = Color(0xFF2B2A29),
    surfaceContainerHighest = Color(0xFF363433),
    primaryFixed = Color(0xFFEBE1D6),
    onPrimaryFixed = Color(0xFF1F1B14),
    primaryFixedDim = Color(0xFFCEC5BA),
    onPrimaryFixedVariant = Color(0xFF4C463E),
    secondaryFixed = Color(0xFFFBDEBF),
    onSecondaryFixed = Color(0xFF271906),
    secondaryFixedDim = Color(0xFFDDC2A4),
    onSecondaryFixedVariant = Color(0xFF56432D),
    tertiaryFixed = Color(0xFFF4DFC9),
    onTertiaryFixed = Color(0xFF241A0C),
    tertiaryFixedDim = Color(0xFFD7C3AE),
    onTertiaryFixedVariant = Color(0xFF524534),
)

// Earthy Tones design system (light) — warm off-white counterpart of the dark scheme
val EarthyLightColorScheme = ColorScheme(
    primary = Color(0xFF6A5D4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E2D3),
    onPrimaryContainer = Color(0xFF241A0E),
    inversePrimary = Color(0xFFD9C9B6),
    secondary = Color(0xFF8A7056),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5DEC4),
    onSecondaryContainer = Color(0xFF2B1C0A),
    tertiary = Color(0xFF6E5C49),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E5D2),
    onTertiaryContainer = Color(0xFF281A0B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF7F2),
    onBackground = Color(0xFF1D1B19),
    surface = Color(0xFFFBF7F2),
    onSurface = Color(0xFF1D1B19),
    surfaceVariant = Color(0xFFE3DCD3),
    onSurfaceVariant = Color(0xFF4A463F),
    surfaceTint = Color(0xFF6A5D4E),
    inverseSurface = Color(0xFF322E29),
    inverseOnSurface = Color(0xFFF2ECE4),
    outline = Color(0xFF83786C),
    outlineVariant = Color(0xFFD4C8BA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF7F2),
    surfaceDim = Color(0xFFDCD5CB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F0E9),
    surfaceContainer = Color(0xFFEFE9E1),
    surfaceContainerHigh = Color(0xFFE9E2D9),
    surfaceContainerHighest = Color(0xFFE4DCD3),
    primaryFixed = Color(0xFFF0E2D3),
    onPrimaryFixed = Color(0xFF241A0E),
    primaryFixedDim = Color(0xFFD9C9B6),
    onPrimaryFixedVariant = Color(0xFF524537),
    secondaryFixed = Color(0xFFF5DEC4),
    onSecondaryFixed = Color(0xFF2B1C0A),
    secondaryFixedDim = Color(0xFFD9C1A6),
    onSecondaryFixedVariant = Color(0xFF6F563E),
    tertiaryFixed = Color(0xFFF8E5D2),
    onTertiaryFixed = Color(0xFF281A0B),
    tertiaryFixedDim = Color(0xFFDCC9B4),
    onTertiaryFixedVariant = Color(0xFF554432),
)

// Rounded shape language from the design system (sm 4 / 8 / 12 / 16 / 24 / pill)
val EarthyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SoundsphereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    // When the default theme color is selected, use the fixed Earthy Tones
    // design system palette instead of system dynamic colors. The legacy
    // default is still honored so existing installs keep the Earthy palette.
    val useEarthyPalette =
        themeColor == DefaultThemeColor ||
            themeColor == LegacyDefaultThemeColor

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useEarthyPalette) {
        if (darkTheme) EarthyDarkColorScheme else EarthyLightColorScheme
    } else {
        // Use materialKolor when a specific seed color is provided. This runs on
        // all API levels: on Android 12+ the system dynamic color functions
        // (dynamicDarkColorScheme/dynamicLightColorScheme) only read the wallpaper
        // and ignore the seed, which made palette changes appear to do nothing.
        rememberDynamicColorScheme(
            seedColor = themeColor, // themeColor is guaranteed non-default here
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot // Keep existing style
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // Use the defined AppTypography
        shapes = EarthyShapes,
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
