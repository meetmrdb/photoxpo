package app.photoxpo.onboarding

/**
 * Estimates the phone space every Preview will use.
 * The sample is one representative Preview. Multiply its bytes by [libraryCount].
 * Negative inputs count as 0. The result stays at or above 0.
 * A product past [Long.MAX_VALUE] saturates at [Long.MAX_VALUE].
 */
fun projectedTotalBytes(
    samplePreviewBytes: Long,
    libraryCount: Int,
): Long {
    val preview = samplePreviewBytes.coerceAtLeast(0L)
    val count = libraryCount.toLong().coerceAtLeast(0L)
    return saturatingMultiply(preview, count)
}

private fun saturatingMultiply(left: Long, right: Long): Long {
    if (left == 0L || right == 0L) return 0L
    if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE
    return left * right
}
