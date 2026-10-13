package app.photoxpo.core

import java.io.InputStream

interface PreviewMaker {
    fun makePreview(original: InputStream, kind: MediaKind): ByteArray
}
