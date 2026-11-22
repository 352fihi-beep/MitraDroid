package com.mitra.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitra.android.R

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

private val TealPrimary = Color(0xFF00BFA6)
private val TealOnPrimary = Color(0xFF00201B)
private val TealContainerDark = Color(0xFF004D42)
private val TealOnContainerDark = Color(0xFF7AF8E1)

private val CobaltSecondary = Color(0xFF3B82F6)
private val CobaltContainerDark = Color(0xFF172D56)
private val CobaltOnContainerDark = Color(0xFFD6E3FF)

private val AmberTertiary = Color(0xFFF59E0B)
private val AmberContainerDark = Color(0xFF4A2E00)
private val AmberOnContainerDark = Color(0xFFFFDEA6)

private val SlateDarkBackground = Color(0xFF0B1220)
private val SlateDarkSurface = Color(0xFF111A2C)
private val SlateDarkSurfaceVariant = Color(0xFF1A263D)
private val SlateDarkOnSurface = Color(0xFFE6EDF7)
private val SlateDarkOnSurfaceVariant = Color(0xFF9FB0C9)

private val LightBackground = Color(0xFFF6F8FB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE7EEF7)
private val LightOnSurface = Color(0xFF0F172A)
private val LightOnSurfaceVariant = Color(0xFF475569)

private fun darkPaletteFor(accent: String) = when (accent) {
    "CobaltPulse" -> darkColorScheme(
        primary = CobaltSecondary,
        onPrimary = Color.White,
        primaryContainer = CobaltContainerDark,
        onPrimaryContainer = CobaltOnContainerDark,
        secondary = TealPrimary,
        onSecondary = TealOnPrimary,
        secondaryContainer = TealContainerDark,
        onSecondaryContainer = TealOnContainerDark,
        tertiary = AmberTertiary,
        tertiaryContainer = AmberContainerDark,
        onTertiaryContainer = AmberOnContainerDark,
        background = SlateDarkBackground,
        onBackground = SlateDarkOnSurface,
        surface = SlateDarkSurface,
        onSurface = SlateDarkOnSurface,
        surfaceVariant = SlateDarkSurfaceVariant,
        onSurfaceVariant = SlateDarkOnSurfaceVariant
    )
    "AmberEdge" -> darkColorScheme(
        primary = AmberTertiary,
        onPrimary = Color(0xFF2B1700),
        primaryContainer = AmberContainerDark,
        onPrimaryContainer = AmberOnContainerDark,
        secondary = TealPrimary,
        onSecondary = TealOnPrimary,
        secondaryContainer = TealContainerDark,
        onSecondaryContainer = TealOnContainerDark,
        tertiary = CobaltSecondary,
        tertiaryContainer = CobaltContainerDark,
        onTertiaryContainer = CobaltOnContainerDark,
        background = SlateDarkBackground,
        onBackground = SlateDarkOnSurface,
        surface = SlateDarkSurface,
        onSurface = SlateDarkOnSurface,
        surfaceVariant = SlateDarkSurfaceVariant,
        onSurfaceVariant = SlateDarkOnSurfaceVariant
    )
    else -> darkColorScheme(
        primary = TealPrimary,
        onPrimary = TealOnPrimary,
        primaryContainer = TealContainerDark,
        onPrimaryContainer = TealOnContainerDark,
        secondary = CobaltSecondary,
        onSecondary = Color.White,
        secondaryContainer = CobaltContainerDark,
        onSecondaryContainer = CobaltOnContainerDark,
        tertiary = AmberTertiary,
        tertiaryContainer = AmberContainerDark,
        onTertiaryContainer = AmberOnContainerDark,
        background = SlateDarkBackground,
        onBackground = SlateDarkOnSurface,
        surface = SlateDarkSurface,
        onSurface = SlateDarkOnSurface,
        surfaceVariant = SlateDarkSurfaceVariant,
        onSurfaceVariant = SlateDarkOnSurfaceVariant
    )
}

private fun lightPaletteFor(accent: String) = when (accent) {
    "CobaltPulse" -> lightColorScheme(
        primary = Color(0xFF1D4ED8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEAFE),
        onPrimaryContainer = Color(0xFF1E3A8A),
        secondary = Color(0xFF00897B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCCFBF1),
        onSecondaryContainer = Color(0xFF003D36),
        tertiary = Color(0xFFD97706),
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant
    )
    "AmberEdge" -> lightColorScheme(
        primary = Color(0xFFD97706),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFEF3C7),
        onPrimaryContainer = Color(0xFF78350F),
        secondary = Color(0xFF00897B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCCFBF1),
        onSecondaryContainer = Color(0xFF003D36),
        tertiary = Color(0xFF2563EB),
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant
    )
    else -> lightColorScheme(
        primary = Color(0xFF00897B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCCFBF1),
        onPrimaryContainer = Color(0xFF003D36),
        secondary = Color(0xFF2563EB),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDBEAFE),
        onSecondaryContainer = Color(0xFF1E3A8A),
        tertiary = Color(0xFFD97706),
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant
    )
}

val MitraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val MitraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MitraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentTheme: String = "ElectricTeal",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkPaletteFor(accentTheme)
        else -> lightPaletteFor(accentTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MitraTypography,
        shapes = MitraShapes,
        content = content
    )
}
