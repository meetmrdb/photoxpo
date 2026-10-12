package app.photoxpo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours for text and controls drawn on top of photos. They stay the same in
 * light and dark themes because the photo, not the theme, sits behind them.
 */
object PhotoOverlay {
    val Scrim = Color(0x9E0A1010)
    val OnScrim = Color(0xFFFFFFFF)
    val Backdrop = Color(0xFF000000)
    val Track = Color(0x30FFFFFF)
    val Ring = Color(0x26FFFFFF)
    val RingPressed = Color(0x44FFFFFF)
    val Mint = Color(0xFF7BD5DA)
    val OnMint = Color(0xFF003739)
    val TealChip = Color(0xCC0B5C63)
    val LoadCard = Color(0xEE1D2424)
    val OnLoadCard = Color(0xFFEEF2F2)
    val TopGradient = Color(0xB3000000)
    val BottomGradient = Color(0xCC000000)
}
