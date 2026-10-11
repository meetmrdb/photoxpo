package app.photoxpo.data

internal data class ArchivePath(
    val segments: List<String>,
)

internal fun parseArchivePath(path: String): ArchivePath {
    val segments = ArrayList<String>()
    for (segment in path.split('/')) {
        if (segment.isEmpty() || segment == ".") continue
        if (segment == "..") error("path escapes the Archive Drive")
        segments += segment
    }
    return ArchivePath(segments)
}

internal fun archivePathKey(segments: List<String>): String = segments.joinToString("/")
