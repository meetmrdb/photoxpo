package app.photoxpo.core

enum class PreviewPreset(
    val longEdgePx: Int,
    val jpegQuality: Int,
) {
    SMALLER(longEdgePx = 480, jpegQuality = 60),
    BALANCED(longEdgePx = 720, jpegQuality = 70),
    SHARPER(longEdgePx = 1080, jpegQuality = 80),
    ;

    fun wireName(): String = when (this) {
        SMALLER -> "smaller"
        BALANCED -> "balanced"
        SHARPER -> "sharper"
    }
}
