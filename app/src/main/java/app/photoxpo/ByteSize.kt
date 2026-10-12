package app.photoxpo

import kotlin.math.roundToInt

/** 1024-based. A negative count is shown as 0 bytes. */
fun formatByteCount(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) {
        return if (safe == 1L) "1 byte" else "$safe bytes"
    }
    val value = safe.toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0
    return when {
        value < mb -> "${oneDecimal(value / kb)} KB"
        value < gb -> "${oneDecimal(value / mb)} MB"
        value < tb -> "${oneDecimal(value / gb)} GB"
        else -> "${oneDecimal(value / tb)} TB"
    }
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole" else "$whole.$fraction"
}
