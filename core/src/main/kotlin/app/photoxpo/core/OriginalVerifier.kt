package app.photoxpo.core

import java.io.OutputStream
import java.time.Clock

/** The result of re-reading an Original and its copy on the Archive Drive. Only [Identical] allows removal. */
sealed interface Verification {
    data class Identical(val hash: String) : Verification

    /** Both sides could be read and they are not the same. */
    data class Differs(val reason: String) : Verification

    /** A side could not be read (drive out, read error). Treated like a failed check, never as identical. */
    data class CouldNotCheck(val reason: String) : Verification
}

/**
 * Re-reads the phone Original and its Archive Drive copy and compares hash and bytes.
 * It never trusts an earlier check time: every call reads both sides again.
 * Anything other than a clean match, including any exception, is not [Verification.Identical].
 */
class OriginalVerifier(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
) {
    fun verify(file: MediaFile, expectedHash: String, drivePath: String, driveSize: Long): Verification {
        return try {
            val phone = mediaSource.openOriginal(file.id).use { input ->
                copyHashed(input, OutputStream.nullOutputStream())
            }
            if (phone.byteCount != file.sizeBytes) {
                return Verification.Differs("The Original on the phone changed size")
            }
            if (phone.sha256 != expectedHash) {
                return Verification.Differs("The Original on the phone changed since it was Backed up")
            }
            if (!archiveDrive.exists(drivePath)) {
                return Verification.Differs("The copy is missing from the Archive Drive")
            }
            if (archiveDrive.byteCount(drivePath) != driveSize || driveSize != phone.byteCount) {
                return Verification.Differs("The copy on the Archive Drive has a different size")
            }
            val onDrive = archiveDrive.read(drivePath).use { input ->
                copyHashed(input, OutputStream.nullOutputStream())
            }
            if (onDrive.byteCount != phone.byteCount || onDrive.sha256 != expectedHash) {
                Verification.Differs("The copy on the Archive Drive is not identical")
            } else {
                Verification.Identical(expectedHash)
            }
        } catch (error: Exception) {
            Verification.CouldNotCheck(error.message ?: "Could not read the file")
        }
    }
}

data class SkippedOriginal(val file: MediaFile, val reason: String)

data class FreeUpResult(
    val removed: List<MediaFile>,
    val skipped: List<SkippedOriginal>,
)

/**
 * Free up space. Re-verifies each Original just before [removeVerified] hands it to `remove`.
 * An Original is removed only when its check comes back [Verification.Identical]; a failed,
 * missing or errored check skips it. Check times in the ledger are never used to skip a check.
 */
class FreeUpSpace(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
    private val backedUpLedger: BackedUpLedger,
    @Suppress("unused") private val clock: Clock = Clock.systemUTC(),
) {
    fun removeVerified(candidates: List<MediaFile>, remove: (MediaFile) -> Unit): FreeUpResult {
        val removed = mutableListOf<MediaFile>()
        val skipped = mutableListOf<SkippedOriginal>()
        val verifier = OriginalVerifier(mediaSource, archiveDrive)
        val entries = try {
            entriesByHash(archiveDrive)
        } catch (error: Exception) {
            val reason = error.message ?: "Could not read the Archive Drive"
            return FreeUpResult(emptyList(), candidates.map { SkippedOriginal(it, reason) })
        }
        for (file in candidates) {
            val hash = backedUpLedger.checkedOriginal(file.id)?.hash
            val entry = hash?.let { entries[it] }
            if (hash == null || entry == null) {
                skipped += SkippedOriginal(file, "Not Backed up on this Archive Drive")
                continue
            }
            when (val check = verifier.verify(file, hash, entry.drivePath, entry.size)) {
                is Verification.Identical -> try {
                    remove(file)
                    removed += file
                } catch (error: Exception) {
                    skipped += SkippedOriginal(file, error.message ?: "Could not remove")
                }
                is Verification.Differs -> skipped += SkippedOriginal(file, check.reason)
                is Verification.CouldNotCheck -> skipped += SkippedOriginal(file, check.reason)
            }
        }
        return FreeUpResult(removed, skipped)
    }
}

data class DifferingOriginal(val file: MediaFile, val reason: String)

data class VerifyEverythingReport(
    /** Originals that were re-read and came back identical or differing. */
    val checked: Int,
    val differing: List<DifferingOriginal>,
    /** Originals that could not be read this time (for example the Archive Drive was unplugged). */
    val couldNotCheck: List<DifferingOriginal>,
)

/**
 * "Verify everything" in Backup history. Re-reads every Backed up Original and its copy, ignoring check times,
 * and reports any that differ. A new check time is stored only for Originals that came back identical.
 */
class VerifyEverything(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
    private val backedUpLedger: BackedUpLedger,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun run(): VerifyEverythingReport {
        val verifier = OriginalVerifier(mediaSource, archiveDrive)
        val backedUp = mediaSource.listCameraFolder().filter { backedUpLedger.checkedOriginal(it.id) != null }
        val entries = try {
            entriesByHash(archiveDrive)
        } catch (error: Exception) {
            val reason = error.message ?: "Could not read the Archive Drive"
            return VerifyEverythingReport(0, emptyList(), backedUp.map { DifferingOriginal(it, reason) })
        }
        var checked = 0
        val differing = mutableListOf<DifferingOriginal>()
        val couldNotCheck = mutableListOf<DifferingOriginal>()
        for (file in backedUp) {
            val record = backedUpLedger.checkedOriginal(file.id) ?: continue
            val entry = entries[record.hash]
            if (entry == null) {
                checked += 1
                differing += DifferingOriginal(file, "Not listed on the Archive Drive")
                continue
            }
            when (val check = verifier.verify(file, record.hash, entry.drivePath, entry.size)) {
                is Verification.Identical -> {
                    checked += 1
                    backedUpLedger.recordChecked(file, record.hash, clock.instant())
                }
                is Verification.Differs -> {
                    checked += 1
                    differing += DifferingOriginal(file, check.reason)
                }
                is Verification.CouldNotCheck -> couldNotCheck += DifferingOriginal(file, check.reason)
            }
        }
        return VerifyEverythingReport(checked, differing, couldNotCheck)
    }
}

private fun entriesByHash(archiveDrive: ArchiveDrive): Map<String, ManifestFileEntry> {
    val byHash = linkedMapOf<String, ManifestFileEntry>()
    for (entry in ManifestReader().readAll(archiveDrive).flatMap { it.entries }) {
        byHash.putIfAbsent(entry.hash, entry)
    }
    return byHash
}
