package app.photoxpo.core

import java.io.InputStream

class FakePreviewMaker : PreviewMaker {
    override fun makePreview(original: InputStream, kind: MediaKind): ByteArray = ByteArray(0)
}
