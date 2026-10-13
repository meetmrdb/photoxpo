package app.photoxpo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.photoxpo.R

// The Quiet Shelf: one teal voice, warm amber for "waiting", red only for real failures.
// Values come from DESIGN.md; edit them there first.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B5C63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4EBEA),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF486462),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E5),
    onSecondaryContainer = Color(0xFF05201E),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA1),
    onTertiaryContainer = Color(0xFF3E2D00),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFEAF1F0),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F4),
    surfaceContainer = Color(0xFFEAF1F0),
    surfaceContainerHigh = Color(0xFFEAF1F0),
    surfaceContainerHighest = Color(0xFFDDE7E6),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF0A1010),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7BD5DA),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFFC4EBEA),
    secondary = Color(0xFFB0CCC9),
    onSecondary = Color(0xFF1B3533),
    secondaryContainer = Color(0xFF2F4B49),
    onSecondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFFF2C14E),
    onTertiary = Color(0xFF3E2D00),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFDEA1),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF192020),
    onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF141B1B),
    surfaceContainer = Color(0xFF192020),
    surfaceContainerHigh = Color(0xFF192020),
    surfaceContainerHighest = Color(0xFF252D2D),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4948),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF0A1010),
)

private val Roboto = FontFamily.Default

// The wordmark's own face, for text of 20 sp or more: headline, titles, month names, dialog titles. Single weight, made for large sizes.
private val InstrumentSerif = FontFamily(Font(R.font.instrument_serif))

private val PhotoxpoTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    // Dialog titles. Dialogs set this explicitly: Material's own default is headlineSmall, too big for a dialog.
    headlineMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    // Top bar titles and month names. The serif is only for text of 20 sp or more, never on controls or body.
    titleLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.24.sp,
    ),
)

private val PhotoxpoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PhotoxpoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PhotoxpoTypography,
        shapes = PhotoxpoShapes,
        content = content,
    )
}
