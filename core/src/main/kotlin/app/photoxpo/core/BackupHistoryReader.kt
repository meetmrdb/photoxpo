package app.photoxpo.core

/**
 * Reads Backup history from run files on the Archive Drive.
 *
 * A truncated last line is ignored. A run file with no footer is interrupted.
 * File count and bytes count committed file lines. Elapsed time and MB/s
 * are taken from the footer.
 */
class BackupHistoryReader {
    /**
     * Reads run files in `Photoxpo/.photoxpo/runs` whose names are not in [knownRunFiles].
     *
     * [knownRunFiles] uses the same name as [BackupRunSummary.runFile], the jsonl
     * file name and not the full path. This returns only the run files it read.
     * A Backup run that was interrupted last time has to be read again, because
     * a later footer changes its file count, bytes, and speed. Leave that run
     * file out of [knownRunFiles].
     */
    fun read(drive: ArchiveDrive, knownRunFiles: Set<String>): List<BackupRunSummary> {
        return drive.list(BACKUP_RUNS_DIRECTORY)
            .asSequence()
            .filter { name -> isRunFile(name) && name !in knownRunFiles }
            .sorted()
            .mapNotNull { name ->
                summary(name, drive.readText("$BACKUP_RUNS_DIRECTORY/$name"))
            }
            .toList()
    }
}

private fun isRunFile(name: String): Boolean {
    return name.endsWith(".jsonl") && !name.contains('/') && name != "." && name != ".."
}

private fun summary(runFile: String, text: String): BackupRunSummary? {
    var startedAt: String? = null
    var footer: FooterFields? = null
    var fileCount = 0L
    var bytes = 0L
    for (raw in completeLines(text)) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val fields = RunJsonParser(line).parse() ?: continue
        when (fields.text("type")) {
            "header" -> if (startedAt == null) startedAt = fields.text("startedAt")
            "file" -> if (fields.isCommittedFile()) {
                fileCount += 1
                bytes += fields.long("size") ?: 0L
            }
            "footer" -> footer = fields.toFooter()
        }
    }
    val start = startedAt ?: footer?.startedAt ?: return null
    val interrupted = footer == null
    return BackupRunSummary(
        runFile = runFile,
        startedAt = start,
        endedAt = if (interrupted) null else footer?.endedAt,
        fileCount = fileCount,
        bytes = bytes,
        checked = !interrupted && footer?.checked == true,
        elapsedMs = if (interrupted) null else footer?.elapsedMs,
        mbPerSecond = if (interrupted) null else footer?.mbPerSecond,
        interrupted = interrupted,
    )
}

private data class FooterFields(
    val startedAt: String?,
    val endedAt: String?,
    val checked: Boolean,
    val elapsedMs: Long?,
    val mbPerSecond: Double?,
)

/**
 * Drops a last line that was cut off. The run file is append-only, so a kill
 * can leave a partial line at the end. That line is not a committed record.
 */
private fun completeLines(text: String): List<String> {
    val endExclusive = when {
        text.endsWith("\n") -> text.length
        else -> text.lastIndexOf('\n')
    }
    if (endExclusive <= 0) return emptyList()
    return text.substring(0, endExclusive).split('\n').map { it.removeSuffix("\r") }
}

private fun Map<String, RunJsonField>.text(name: String): String? = (this[name] as? RunJsonField.Text)?.value

private fun Map<String, RunJsonField>.long(name: String): Long? = (this[name] as? RunJsonField.Integer)?.value

private fun Map<String, RunJsonField>.double(name: String): Double? = when (val field = this[name]) {
    is RunJsonField.Integer -> field.value.toDouble()
    is RunJsonField.Decimal -> field.value
    else -> null
}

private fun Map<String, RunJsonField>.bool(name: String): Boolean? = when (this[name]) {
    RunJsonField.True -> true
    RunJsonField.False -> false
    else -> null
}

private fun Map<String, RunJsonField>.isCommittedFile(): Boolean {
    val hash = text("hash") ?: return false
    val drivePath = text("drivePath") ?: return false
    val size = long("size") ?: return false
    val originalFilename = text("originalFilename") ?: return false
    return hash.isNotEmpty() && drivePath.isNotEmpty() && originalFilename.isNotEmpty() && size >= 0
}

private fun Map<String, RunJsonField>.toFooter(): FooterFields {
    return FooterFields(
        startedAt = text("startedAt"),
        endedAt = text("endedAt"),
        checked = bool("checked") == true,
        elapsedMs = long("elapsedMs"),
        mbPerSecond = double("mbPerSecond"),
    )
}

private sealed interface RunJsonField {
    data class Text(val value: String) : RunJsonField
    data class Integer(val value: Long) : RunJsonField
    data class Decimal(val value: Double) : RunJsonField
    data object True : RunJsonField
    data object False : RunJsonField
    data object Null : RunJsonField
    data object Skipped : RunJsonField
}

private class RunJsonParser(private val line: String) {
    private var index = 0

    fun parse(): Map<String, RunJsonField>? {
        skipWs()
        if (!eat('{')) return null
        skipWs()
        val fields = mutableMapOf<String, RunJsonField>()
        if (eat('}')) {
            skipWs()
            return if (index == line.length) fields else null
        }
        while (index < line.length) {
            skipWs()
            val key = parseString() ?: return null
            skipWs()
            if (!eat(':')) return null
            skipWs()
            val value = parseValue() ?: return null
            if (value !is RunJsonField.Skipped) fields[key] = value
            skipWs()
            if (eat('}')) {
                skipWs()
                return if (index == line.length) fields else null
            }
            if (!eat(',')) return null
        }
        return null
    }

    private fun parseValue(): RunJsonField? {
        if (index >= line.length) return null
        return when (line[index]) {
            '"' -> parseString()?.let { RunJsonField.Text(it) }
            '[' -> if (skipContainer('[', ']')) RunJsonField.Skipped else null
            '{' -> if (skipContainer('{', '}')) RunJsonField.Skipped else null
            't' -> if (literal("true")) RunJsonField.True else null
            'f' -> if (literal("false")) RunJsonField.False else null
            'n' -> if (literal("null")) RunJsonField.Null else null
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber()
            else -> null
        }
    }

    private fun skipContainer(open: Char, close: Char): Boolean {
        if (!eat(open)) return false
        var depth = 1
        var inString = false
        var escaped = false
        while (index < line.length && depth > 0) {
            val ch = line[index]
            index++
            when {
                inString && escaped -> escaped = false
                inString && ch == '\\' -> escaped = true
                inString && ch == '"' -> inString = false
                inString -> Unit
                ch == '"' -> inString = true
                ch == open -> depth++
                ch == close -> depth--
            }
        }
        return depth == 0
    }

    private fun parseNumber(): RunJsonField? {
        val start = index
        if (line[index] == '-') index++
        if (index >= line.length || !line[index].isDigit()) return null
        while (index < line.length && line[index].isDigit()) index++
        var decimal = false
        if (index < line.length && line[index] == '.') {
            decimal = true
            index++
            if (index >= line.length || !line[index].isDigit()) return null
            while (index < line.length && line[index].isDigit()) index++
        }
        if (index < line.length && (line[index] == 'e' || line[index] == 'E')) {
            decimal = true
            index++
            if (index < line.length && (line[index] == '+' || line[index] == '-')) index++
            if (index >= line.length || !line[index].isDigit()) return null
            while (index < line.length && line[index].isDigit()) index++
        }
        val raw = line.substring(start, index)
        if (!decimal) {
            val value = raw.toLongOrNull() ?: return null
            return RunJsonField.Integer(value)
        }
        val value = raw.toDoubleOrNull() ?: return null
        return RunJsonField.Decimal(value)
    }

    private fun parseString(): String? {
        if (!eat('"')) return null
        val out = StringBuilder()
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' -> {
                    index++
                    return out.toString()
                }
                ch == '\\' -> if (!decodeEscape(out)) return null
                ch.code < 0x20 -> return null
                else -> {
                    out.append(ch)
                    index++
                }
            }
        }
        return null
    }

    private fun decodeEscape(out: StringBuilder): Boolean {
        if (index + 1 >= line.length) return false
        when (val esc = line[index + 1]) {
            '"', '\\', '/' -> {
                out.append(esc)
                index += 2
            }
            'b' -> {
                out.append('\b')
                index += 2
            }
            'f' -> {
                out.append('\u000C')
                index += 2
            }
            'n' -> {
                out.append('\n')
                index += 2
            }
            'r' -> {
                out.append('\r')
                index += 2
            }
            't' -> {
                out.append('\t')
                index += 2
            }
            'u' -> {
                if (index + 6 > line.length) return false
                val hex = line.substring(index + 2, index + 6)
                val code = hex.toIntOrNull(16) ?: return false
                out.append(code.toChar())
                index += 6
            }
            else -> return false
        }
        return true
    }

    private fun literal(token: String): Boolean {
        if (!line.startsWith(token, index)) return false
        val end = index + token.length
        if (end < line.length && line[end].isLetterOrDigit()) return false
        index = end
        return true
    }

    private fun eat(ch: Char): Boolean {
        if (index < line.length && line[index] == ch) {
            index++
            return true
        }
        return false
    }

    private fun skipWs() {
        while (index < line.length && line[index].isWhitespace()) index++
    }
}
