package app.photoxpo.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.withStyle

private val FigurePattern = Regex("[0-9]+(?:[.,][0-9]+)*")

/** Where the counts, sizes and years sit in [text]. A trailing full stop is not part of a figure. */
internal fun figureRuns(text: String): List<IntRange> =
    FigurePattern.findAll(text).map { it.range }.toList()

/**
 * Instrument Serif's "1" has no flag and reads as a lowercase L, so every figure in a serif line is
 * set in the device sans. [figureSize] lets a figure match the serif's optical size; unspecified keeps the line's size.
 */
fun figuresInSans(text: String, figureSize: TextUnit = TextUnit.Unspecified): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        for (run in figureRuns(text)) {
            append(text.substring(last, run.first))
            withStyle(SpanStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = figureSize)) {
                append(text.substring(run))
            }
            last = run.last + 1
        }
        append(text.substring(last))
    }
