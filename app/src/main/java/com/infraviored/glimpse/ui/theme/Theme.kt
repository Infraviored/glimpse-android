package com.infraviored.glimpse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.infraviored.glimpse.R

/*
 * Glimpse's palette comes from its icon: a night-blue field, the blue-to-violet of the lens
 * ring, the apricot of the sunset inside it. Photos are the colour; the interface stays in
 * the night blue around them and uses apricot only where something is chosen.
 */
object Palette {
    val Night = Color(0xFF14162B)
    val Raised = Color(0xFF1D2040)
    val Line = Color(0xFF2E3260)
    val Fog = Color(0xFFECEDF7)
    val Mist = Color(0xFF9599BE)
    val LensBlue = Color(0xFF2B7DE9)
    val LensViolet = Color(0xFF6A3FD8)
    val Apricot = Color(0xFFFFB27A)
    val ApricotDeep = Color(0xFFB8541A)

    val Paper = Color(0xFFF5F5FA)
    val Card = Color(0xFFFFFFFF)
    val PaperLine = Color(0xFFE1E2EE)
    val Ink = Color(0xFF191B33)
    val Slate = Color(0xFF5E6286)

    val Lens = Brush.linearGradient(listOf(LensBlue, LensViolet))
}

/** What Material's scheme has no slot for. */
@Immutable
data class GlimpseColors(
    val background: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    /** Chosen things: selection marks, the active filter. */
    val accent: Color,
    /** Accent as text on [background]: apricot is too light on paper. */
    val accentText: Color,
    val isDark: Boolean,
)

val LocalGlimpseColors = staticCompositionLocalOf {
    GlimpseColors(Palette.Night, Palette.Raised, Palette.Line, Palette.Fog, Palette.Mist, Palette.Apricot, Palette.Apricot, true)
}

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float, opsz: Float) = Font(
    R.font.bricolage,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("opsz", opsz),
    ),
)

/** Big, slightly condensed: month and year headings, names. */
val Display = FontFamily(bricolage(720, 82f, 72f))
val DisplayRegular = FontFamily(bricolage(560, 90f, 36f))
val Heading = FontFamily(bricolage(640, 92f, 24f))

private val GlimpseTypography = Typography().let { base ->
    base.copy(
        displayLarge = TextStyle(fontFamily = Display, fontSize = 96.sp, lineHeight = 0.9.em, letterSpacing = (-0.03).em),
        displayMedium = TextStyle(fontFamily = Display, fontSize = 44.sp, lineHeight = 1.0.em, letterSpacing = (-0.02).em),
        displaySmall = TextStyle(fontFamily = Display, fontSize = 34.sp, lineHeight = 1.05.em, letterSpacing = (-0.015).em),
        headlineSmall = TextStyle(fontFamily = Heading, fontSize = 24.sp, lineHeight = 1.15.em),
        titleLarge = TextStyle(fontFamily = Heading, fontSize = 20.sp, lineHeight = 1.2.em),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun GlimpseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) {
        GlimpseColors(Palette.Night, Palette.Raised, Palette.Line, Palette.Fog, Palette.Mist, Palette.Apricot, Palette.Apricot, true)
    } else {
        GlimpseColors(Palette.Paper, Palette.Card, Palette.PaperLine, Palette.Ink, Palette.Slate, Palette.Apricot, Palette.ApricotDeep, false)
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.LensBlue,
            onPrimary = Color.White,
            secondary = Palette.Apricot,
            onSecondary = Palette.Night,
            background = Palette.Night,
            onBackground = Palette.Fog,
            surface = Palette.Night,
            onSurface = Palette.Fog,
            surfaceVariant = Palette.Raised,
            onSurfaceVariant = Palette.Mist,
            surfaceContainer = Palette.Raised,
            surfaceContainerHigh = Palette.Raised,
            surfaceContainerLow = Palette.Night,
            outline = Palette.Line,
            outlineVariant = Palette.Line,
            error = Color(0xFFFF8A8A),
        )
    } else {
        lightColorScheme(
            primary = Palette.LensBlue,
            onPrimary = Color.White,
            secondary = Palette.ApricotDeep,
            onSecondary = Color.White,
            background = Palette.Paper,
            onBackground = Palette.Ink,
            surface = Palette.Paper,
            onSurface = Palette.Ink,
            surfaceVariant = Palette.Card,
            onSurfaceVariant = Palette.Slate,
            surfaceContainer = Palette.Card,
            surfaceContainerHigh = Palette.Card,
            surfaceContainerLow = Palette.Paper,
            outline = Palette.PaperLine,
            outlineVariant = Palette.PaperLine,
        )
    }
    CompositionLocalProvider(LocalGlimpseColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = GlimpseTypography, content = content)
    }
}

val glimpse: GlimpseColors
    @Composable get() = LocalGlimpseColors.current
