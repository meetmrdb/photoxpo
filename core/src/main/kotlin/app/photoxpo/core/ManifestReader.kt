package app.photoxpo.core

internal const val BACKUP_RUNS_DIRECTORY = "Photoxpo/.photoxpo/runs"

data class ManifestFileEntry(
    val hash: String,
    val drivePath: String,
    val size: Long,
    val originalFilename: String,
    val originalFilenames: List<String> = listOf(originalFilename),
    val kind: String? = null,
    val chosenDate: String? = null,
    val sourceTag: String? = null,
)

data class ManifestContents(
    val entries: List<ManifestFileEntry>,
    val interrupted: Boolean,
)

class ManifestReader {
    fun read(manifestText: String): ManifestContents {
        val entries = mutableListOf<ManifestFileEntry>()
        var sawFooter = false
        for (raw in completeLines(manifestText)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val fields = JsonObjectParser(line).parse() ?: continue
            when (fields.text("type")) {
                "file" -> fields.toFileEntry()?.let { entries += it }
                "footer" -> sawFooter = true
            }
        }
        return ManifestContents(entries, interrupted = !sawFooter)
    }

    fun readAll(archiveDrive: ArchiveDrive): List<ManifestContents> {
        return archiveDrive.list(BACKUP_RUNS_DIRECTORY)
            .asSequence()
            .filter { name ->
                name.endsWith(".jsonl") && !name.contains('/') && name != "." && name != ".."
            }
            .sorted()
            .map { name -> read(archiveDrive.readText("$BACKUP_RUNS_DIRECTORY/$name")) }
            .toList()
    }
}

private fun completeLines(text: String): List<String> {
    val endExclusive = when {
        text.endsWith("\n") -> text.length
        else -> text.lastIndexOf('\n')
    }
    if (endExclusive <= 0) return emptyList()
    return text.substring(0, endExclusive).split('\n').map { it.removeSuffix("\r") }
}

private fun Map<String, JsonField>.text(name: String): String? = (this[name] as? JsonField.Text)?.value

private fun Map<String, JsonField>.long(name: String): Long? = (this[name] as? JsonField.Integer)?.value

private fun Map<String, JsonField>.toFileEntry(): ManifestFileEntry? {
    val hash = text("hash") ?: return null
    val drivePath = text("drivePath") ?: return null
    val size = long("size") ?: return null
    val listedNames = when (val names = this["originalFilenames"]) {
        null, JsonField.Null -> emptyList()
        is JsonField.Strings -> names.value.filter { it.isNotEmpty() }
        else -> return null
    }
    val originalFilename = text("originalFilename") ?: listedNames.firstOrNull() ?: return null
    if (hash.isEmpty() || drivePath.isEmpty() || originalFilename.isEmpty() || size < 0) return null
    val filenames = when {
        listedNames.isEmpty() -> listOf(originalFilename)
        originalFilename !in listedNames -> listOf(originalFilename) + listedNames
        else -> listedNames
    }
    return ManifestFileEntry(
        hash = hash,
        drivePath = drivePath,
        size = size,
        originalFilename = originalFilename,
        originalFilenames = filenames,
        kind = text("kind"),
        chosenDate = text("chosenDate"),
        sourceTag = text("sourceTag"),
    )
}

private sealed interface JsonField {
    data class Text(val value: String) : JsonField
    data class Strings(val value: List<String>) : JsonField
    data class Integer(val value: Long) : JsonField
    data class Decimal(val raw: String) : JsonField
    data object True : JsonField
    data object False : JsonField
    data object Null : JsonField
}

private class JsonObjectParser(private val line: String) {
    private var index = 0

    fun parse(): Map<String, JsonField>? {
        skipWs()
        if (!eat('{')) return null
        skipWs()
        val fields = mutableMapOf<String, JsonField>()
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
            fields[key] = value
            skipWs()
            if (eat('}')) {
                skipWs()
                return if (index == line.length) fields else null
            }
            if (!eat(',')) return null
        }
        return null
    }

    private fun parseValue(): JsonField? {
        if (index >= line.length) return null
        return when (line[index]) {
            '"' -> parseString()?.let { JsonField.Text(it) }
            '[' -> parseStringArray()
            't' -> if (literal("true")) JsonField.True else null
            'f' -> if (literal("false")) JsonField.False else null
            'n' -> if (literal("null")) JsonField.Null else null
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber()
            else -> null
        }
    }

    private fun parseStringArray(): JsonField? {
        if (!eat('[')) return null
        skipWs()
        if (eat(']')) return JsonField.Strings(emptyList())
        val values = mutableListOf<String>()
        while (true) {
            skipWs()
            val value = parseString() ?: return null
            values += value
            skipWs()
            when {
                eat(']') -> return JsonField.Strings(values)
                eat(',') -> continue
                else -> return null
            }
        }
    }

    private fun parseNumber(): JsonField? {
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
            return JsonField.Integer(value)
        }
        return JsonField.Decimal(raw)
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
