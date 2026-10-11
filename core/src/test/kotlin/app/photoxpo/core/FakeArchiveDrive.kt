package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FakeArchiveDrive(
    private val root: Path,
    private val corruptReadBack: Boolean = false,
    private val wrongByteCount: Boolean = false,
) : ArchiveDrive {
    override fun create(path: String): CreateResult {
        val target = resolve(path)
        if (Files.exists(target)) return CreateResult.Collision(path)
        Files.createDirectories(target.parent)
        Files.createFile(target)
        return CreateResult.Created
    }

    override fun write(path: String): OutputStream {
        return Files.newOutputStream(
            resolve(path),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    override fun read(path: String): InputStream {
        val input = Files.newInputStream(resolve(path))
        return if (corruptReadBack) FirstByteFlippingInputStream(input) else input
    }

    override fun byteCount(path: String): Long {
        val actual = Files.size(resolve(path))
        return if (wrongByteCount) actual + 1 else actual
    }

    override fun rename(from: String, to: String): RenameResult {
        val source = resolve(from)
        val target = resolve(to)
        if (Files.exists(target)) return RenameResult.Collision(to)
        Files.createDirectories(target.parent)
        Files.move(source, target)
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
    }

    override fun delete(path: String) {
        Files.deleteIfExists(resolve(path))
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
