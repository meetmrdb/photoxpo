package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

enum class DriveFaultStep {
    AFTER_CREATE,
    MID_WRITE,
    AFTER_WRITE,
    MID_READ_BACK,
    BEFORE_RENAME,
    AFTER_RENAME_BEFORE_MANIFEST,
    AFTER_MANIFEST_LINE,
}

data class DriveFault(
    val step: DriveFaultStep,
    val error: Throwable,
    val afterBytes: Long = 1,
    val occurrence: Int = 1,
)

class FakeArchiveDrive(
    private val root: Path,
    private val corruptReadBack: Boolean = false,
    private val wrongByteCount: Boolean = false,
) : ArchiveDrive {
    private var stepHits = 0

    var faults: DriveFault? = null
        set(value) {
            field = value
            stepHits = 0
        }

    var writeCalls: Int = 0
        private set

    override fun create(path: String): CreateResult {
        val target = resolve(path)
        if (Files.exists(target)) return CreateResult.Collision(path)
        Files.createDirectories(target.parent)
        Files.createFile(target)
        if (isPart(path)) {
            take(DriveFaultStep.AFTER_CREATE)?.let { throw it.error }
        }
        return CreateResult.Created
    }

    override fun write(path: String): OutputStream {
        writeCalls += 1
        val base = Files.newOutputStream(
            resolve(path),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        if (!isPart(path)) return base
        val fault = take(DriveFaultStep.MID_WRITE) ?: take(DriveFaultStep.AFTER_WRITE)
        return if (fault == null) base else FaultingOutputStream(base, fault)
    }

    override fun read(path: String): InputStream {
        var input: InputStream = Files.newInputStream(resolve(path))
        if (corruptReadBack) input = FirstByteFlippingInputStream(input)
        if (!isPart(path)) return input
        val fault = take(DriveFaultStep.MID_READ_BACK) ?: return input
        return FaultingInputStream(input, fault)
    }

    override fun byteCount(path: String): Long {
        val actual = Files.size(resolve(path))
        return if (wrongByteCount) actual + 1 else actual
    }

    override fun rename(from: String, to: String): RenameResult {
        if (isPart(from)) {
            take(DriveFaultStep.BEFORE_RENAME)?.let { throw it.error }
        }
        val source = resolve(from)
        val target = resolve(to)
        if (Files.exists(target)) return RenameResult.Collision(to)
        Files.createDirectories(target.parent)
        Files.move(source, target)
        if (isPart(from)) {
            take(DriveFaultStep.AFTER_RENAME_BEFORE_MANIFEST)?.let { throw it.error }
        }
        return RenameResult.Renamed
    }

    override fun exists(path: String): Boolean = Files.exists(resolve(path))

    override fun list(directory: String): List<String> {
        val dir = resolve(directory)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.map { it.fileName.toString() }.sorted().toList()
        }
    }

    override fun readText(path: String): String {
        return Files.readString(resolve(path), StandardCharsets.UTF_8)
    }

    override fun appendText(path: String, text: String) {
        val target = resolve(path)
        Files.createDirectories(target.parent)
        Files.writeString(
            target,
            text,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        )
        if (isFileManifestLine(text)) {
            take(DriveFaultStep.AFTER_MANIFEST_LINE)?.let { throw it.error }
        }
    }

    override fun delete(path: String) {
        Files.deleteIfExists(resolve(path))
    }

    private fun take(step: DriveFaultStep): DriveFault? {
        val fault = faults ?: return null
        if (fault.step != step) return null
        stepHits += 1
        if (stepHits < fault.occurrence) return null
        faults = null
        return fault
    }

    private fun resolve(path: String): Path {
        var current = root
        for (segment in path.split('/')) {
            if (segment.isEmpty() || segment == ".") continue
            if (segment == "..") error("path escapes the Archive Drive")
            current = current.resolve(segment)
        }
        return current
    }
}

private fun isPart(path: String): Boolean = path.endsWith(".photoxpo-part")

private fun isFileManifestLine(text: String): Boolean = text.contains("\"type\":\"file\"")

private class FaultingOutputStream(
    private val delegate: OutputStream,
    private val fault: DriveFault,
) : OutputStream() {
    private var written = 0L

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(buffer: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            if (fault.step == DriveFaultStep.MID_WRITE && written >= fault.afterBytes) {
                throw fault.error
            }
            val chunk = if (fault.step == DriveFaultStep.MID_WRITE) {
                val room = fault.afterBytes - written
                if (room <= 0L) throw fault.error
                minOf(remaining.toLong(), room).toInt()
            } else {
                remaining
            }
            delegate.write(buffer, offset, chunk)
            written += chunk
            offset += chunk
            remaining -= chunk
            if (fault.step == DriveFaultStep.MID_WRITE && written >= fault.afterBytes) {
                throw fault.error
            }
        }
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.flush()
        delegate.close()
        if (fault.step == DriveFaultStep.AFTER_WRITE) throw fault.error
    }
}

private class FaultingInputStream(
    private val delegate: InputStream,
    private val fault: DriveFault,
) : InputStream() {
    private var seen = 0L

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        if (n < 0) return -1
        return one[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (seen >= fault.afterBytes) throw fault.error
        val room = fault.afterBytes - seen
        if (room <= 0L) throw fault.error
        val cap = minOf(len.toLong(), room).toInt()
        val n = delegate.read(buffer, off, cap)
        if (n > 0) seen += n
        if (seen >= fault.afterBytes) throw fault.error
        return n
    }

    override fun close() {
        delegate.close()
    }
}

private class FirstByteFlippingInputStream(
    private val delegate: InputStream,
) : InputStream() {
    private var flipped = false

    override fun read(): Int {
        val value = delegate.read()
        if (value < 0 || flipped) return value
        flipped = true
        return value xor 0xFF
    }

    override fun close() {
        delegate.close()
    }
}
