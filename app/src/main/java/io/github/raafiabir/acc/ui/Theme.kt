package io.github.raafiabir.acc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.raafiabir.acc.R

/** The whole palette: neutrals only, matching the logo. Emphasis is ink on paper, never a hue. */
class AccColors(
    val bg: Color, val surface: Color, val ink: Color, val mute: Color,
    val line: Color, val acc: Color, val onAcc: Color,
)

private val LightAcc = AccColors(
    bg = Color(0xFFF4F4F5), surface = Color(0xFFFFFFFF), ink = Color(0xFF0B0B0D), mute = Color(0xFF62626A),
    line = Color(0xFFE4E4E7), acc = Color(0xFF0B0B0D), onAcc = Color(0xFFFFFFFF),
)
private val DarkAcc = AccColors(
    bg = Color(0xFF0B0B0D), surface = Color(0xFF151518), ink = Color(0xFFF2F2F3), mute = Color(0xFF9A9AA2),
    line = Color(0xFF26262B), acc = Color(0xFFF2F2F3), onAcc = Color(0xFF0B0B0D),
)

val LocalAcc = staticCompositionLocalOf { LightAcc }

object Acc {
    val colors: AccColors
        @androidx.compose.runtime.Composable get() = LocalAcc.current
}

// DM Sans (SIL Open Font License), variable font bundled in res/font.
@OptIn(ExperimentalTextApi::class)
private fun dm(weight: Int) = Font(
    R.font.dmsans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val DmSans = FontFamily(dm(300), dm(400), dm(500), dm(600))

object AccType {
    private fun t(size: Int, weight: Int, line: Int = 0, spacing: Float = 0f) = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = (if (line > 0) line else (size * 1.35f).toInt()).sp,
        letterSpacing = spacing.sp,
    )

    val wordmark = t(20, 600, spacing = 0.5f)
    val big = t(76, 300, line = 76, spacing = -3f)
    val unit = t(28, 400)
    val h1 = t(30, 600, spacing = -0.5f)
    val title = t(16, 600)
    val body = t(16, 400)
    val bodyMedium = t(16, 500)
    val caption = t(15, 400)
    val desc = t(14, 400)
    val small = t(13, 400)
    val smallMedium = t(13, 500)
    val smallSemi = t(13, 600)
    val button = t(17, 600)
}

private fun materialScheme(a: AccColors, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = a.acc, onPrimary = a.onAcc, primaryContainer = a.line, onPrimaryContainer = a.ink,
            secondary = a.acc, onSecondary = a.onAcc, secondaryContainer = a.line, onSecondaryContainer = a.ink,
            tertiary = a.acc, onTertiary = a.onAcc, tertiaryContainer = a.line, onTertiaryContainer = a.ink,
            background = a.bg, onBackground = a.ink, surface = a.surface, onSurface = a.ink,
            surfaceVariant = a.line, onSurfaceVariant = a.mute, outline = a.mute, outlineVariant = a.line,
            surfaceTint = a.acc,
            surfaceContainerLowest = a.bg, surfaceContainerLow = a.surface, surfaceContainer = a.surface,
            surfaceContainerHigh = a.surface, surfaceContainerHighest = a.line,
        )
    } else {
        lightColorScheme(
            primary = a.acc, onPrimary = a.onAcc, primaryContainer = a.line, onPrimaryContainer = a.ink,
            secondary = a.acc, onSecondary = a.onAcc, secondaryContainer = a.line, onSecondaryContainer = a.ink,
            tertiary = a.acc, onTertiary = a.onAcc, tertiaryContainer = a.line, onTertiaryContainer = a.ink,
            background = a.bg, onBackground = a.ink, surface = a.surface, onSurface = a.ink,
            surfaceVariant = a.line, onSurfaceVariant = a.mute, outline = a.mute, outlineVariant = a.line,
            surfaceTint = a.acc,
            surfaceContainerLowest = a.bg, surfaceContainerLow = a.surface, surfaceContainer = a.surface,
            surfaceContainerHigh = a.surface, surfaceContainerHighest = a.line,
        )
    }

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

private val AccShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun AccTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val a = if (dark) DarkAcc else LightAcc
    CompositionLocalProvider(LocalAcc provides a) {
        MaterialTheme(
            colorScheme = materialScheme(a, dark),
            typography = Typography().withFont(DmSans),
            shapes = AccShapes,
            content = content,
        )
    }
}
