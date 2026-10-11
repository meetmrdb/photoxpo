package app.photoxpo.onboarding

/**
 * Estimates bytes for every Original plus its Preview.
 * The sample is one representative item. Add its Original bytes to its Preview bytes,
 * then multiply by [libraryCount].
 * Negative inputs count as 0. The result stays at or above 0.
 * A product past [Long.MAX_VALUE] saturates at [Long.MAX_VALUE].
 */
fun projectedTotalBytes(
    sampleOriginalBytes: Long,
    samplePreviewBytes: Long,
    libraryCount: Int,
): Long {
    val original = sampleOriginalBytes.coerceAtLeast(0L)
    val preview = samplePreviewBytes.coerceAtLeast(0L)
    val count = libraryCount.toLong().coerceAtLeast(0L)
    val perItem = saturatingAdd(original, preview)
    return saturatingMultiply(perItem, count)
}

private fun saturatingAdd(left: Long, right: Long): Long {
    if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE
    return left + right
}

private fun saturatingMultiply(left: Long, right: Long): Long {
    if (left == 0L || right == 0L) return 0L
    if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE
    return left * right
}
