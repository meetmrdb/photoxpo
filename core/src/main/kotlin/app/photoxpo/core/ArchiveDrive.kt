package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream

sealed interface CreateResult {
    data object Created : CreateResult

    data class Collision(val path: String) : CreateResult
}

sealed interface RenameResult {
    data object Renamed : RenameResult

    data class Collision(val path: String) : RenameResult
}

interface ArchiveDrive {
    fun create(path: String): CreateResult

    fun write(path: String): OutputStream

    fun read(path: String): InputStream

    fun byteCount(path: String): Long

    fun rename(from: String, to: String): RenameResult

    fun exists(path: String): Boolean

    fun list(directory: String): List<String>

    fun readText(path: String): String

    fun appendText(path: String, text: String)

    fun delete(path: String)
}
