package app.photoxpo.photos

import java.text.NumberFormat
import java.util.Locale

/** The line under the Photos title: how many Originals are on the Archive Drive, and how many new ones wait. */
internal fun photosStripLine(
    onDrive: Int,
    newWaiting: Int?,
    locale: Locale = Locale.getDefault(),
): String {
    val format = NumberFormat.getIntegerInstance(locale)
    val base = if (onDrive > 0) "${format.format(onDrive)} on your Archive Drive" else "Nothing on your Archive Drive yet"
    return if (newWaiting != null && newWaiting > 0) "$base · ${format.format(newWaiting)} new waiting" else base
}
