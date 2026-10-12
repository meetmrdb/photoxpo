package app.photoxpo.onboarding

import app.photoxpo.core.MediaFile
import app.photoxpo.core.MediaKind
import app.photoxpo.core.PreviewPreset
import app.photoxpo.formatByteCount

fun classifyMediaAccess(
    fullAccess: Boolean,
    imagesGranted: Boolean,
    videosGranted: Boolean,
    selectedGranted: Boolean,
): MediaAccessState {
    if (fullAccess) return MediaAccessState.Full
    if (imagesGranted || videosGranted || selectedGranted) return MediaAccessState.Partial
    return MediaAccessState.Denied
}

fun showAccessWarning(access: MediaAccessState?, requested: Boolean): Boolean {
    return when (access) {
        MediaAccessState.Partial -> true
        MediaAccessState.Denied -> requested
        MediaAccessState.Full, null -> false
    }
}

fun cameraPhotos(files: List<MediaFile>): List<MediaFile> {
    return files.filter { it.kind == MediaKind.PHOTO }
}

/** [files] is oldest first, so the last photo is the newest. */
fun newestCameraPhoto(photos: List<MediaFile>): MediaFile? = photos.lastOrNull()

fun anotherCameraPhoto(photos: List<MediaFile>, currentId: String, roll: Int): MediaFile? {
    if (photos.isEmpty()) return null
    val others = photos.filter { it.id != currentId }
    if (others.isEmpty()) return photos.first()
    return others[Math.floorMod(roll, others.size)]
}

fun presetName(preset: PreviewPreset): String {
    return when (preset) {
        PreviewPreset.SMALLER -> "Smaller"
        PreviewPreset.BALANCED -> "Balanced"
        PreviewPreset.SHARPER -> "Sharper"
    }
}

fun presetDescription(preset: PreviewPreset): String {
    return when (preset) {
        PreviewPreset.SMALLER -> "Fits the most. Fine for finding a photo. Soft when you zoom."
        PreviewPreset.BALANCED -> "Looks good on the phone screen."
        PreviewPreset.SHARPER -> "Stays crisp when you zoom in. Takes more room on the phone."
    }
}

fun usePresetLabel(preset: PreviewPreset): String = "Use ${presetName(preset)}"

fun originalSizeLabel(bytes: Long?): String? {
    return bytes?.let { "Original ${formatByteCount(it)}" }
}

data class QualityCardInput(
    val previewBytes: Long? = null,
    val projectedBytes: Long? = null,
    val failed: Boolean = false,
)

data class QualityCardModel(
    val preset: PreviewPreset,
    val name: String,
    val description: String,
    val recommended: Boolean,
    val selected: Boolean,
    val previewSize: String,
    val estimate: String,
)

fun qualityCardModels(
    selected: PreviewPreset,
    inputs: Map<PreviewPreset, QualityCardInput>,
    libraryCount: Int,
): List<QualityCardModel> {
    return PreviewPreset.entries.map { preset ->
        val input = inputs[preset] ?: QualityCardInput()
        QualityCardModel(
            preset = preset,
            name = presetName(preset),
            description = presetDescription(preset),
            recommended = preset == PreviewPreset.BALANCED,
            selected = preset == selected,
            previewSize = previewSizeLabel(input),
            estimate = estimateLabel(input, libraryCount),
        )
    }
}

fun previewSizeLabel(input: QualityCardInput): String {
    return when {
        input.failed -> "Preview could not be made"
        input.previewBytes == null -> "Preview is still working"
        else -> "Preview ${formatByteCount(input.previewBytes)}"
    }
}

fun estimateLabel(input: QualityCardInput, libraryCount: Int): String {
    return when {
        libraryCount <= 0 -> "Nothing to estimate yet"
        input.failed -> "Estimate unavailable"
        input.projectedBytes == null -> "Estimate is still working"
        else -> "${formatByteCount(input.projectedBytes)} estimate"
    }
}

data class ReadyLines(
    val archiveDrive: String,
    val previews: String,
    val note: String?,
    val button: String,
)

fun readyLines(summary: ReadySummary): ReadyLines {
    val drive = if (summary.driveChosen) {
        "Archive Drive chosen"
    } else {
        "Archive Drive not chosen yet"
    }
    val note = if (summary.firstBackupTakesHoursAndCanBePaused) {
        "The first backup takes hours and can be paused."
    } else {
        null
    }
    val button = if (summary.driveChosen) "Start backup" else "Open the app"
    return ReadyLines(
        archiveDrive = drive,
        previews = "Previews ${presetName(summary.preset)}",
        note = note,
        button = button,
    )
}

fun driveReadOnlyMessage(): String {
    return "This Archive Drive can only be read. Choose a folder Photoxpo can write to."
}

fun driveFailureMessage(failure: String?): String {
    val plain = failure?.trim()?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    if (plain.isEmpty() || plain.length > 140) {
        return "Photoxpo could not use that Archive Drive."
    }
    return plain
}

