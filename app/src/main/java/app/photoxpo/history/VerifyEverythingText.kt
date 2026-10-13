package app.photoxpo.history

import app.photoxpo.core.VerifyEverythingReport

/** Plain words for the result of "Verify everything" in Backup history. */
fun verifyEverythingMessage(report: VerifyEverythingReport): String {
    val differing = report.differing.size
    val unchecked = report.couldNotCheck.size
    val parts = mutableListOf<String>()
    if (differing > 0) {
        val names = report.differing.take(3).joinToString(", ") { it.file.name }
        val more = if (differing > 3) " and ${differing - 3} more" else ""
        val lead = if (differing == 1) "1 file is different from its copy: " else "$differing files are different from their copies: "
        parts += lead + names + more
    } else if (report.checked > 0) {
        parts += "All ${report.checked} checked files are identical to their copies"
    }
    if (unchecked > 0) {
        val count = if (unchecked == 1) "1 file" else "$unchecked files"
        parts += "$count could not be checked. Reconnect your drive and try again"
    }
    if (parts.isEmpty()) return "Nothing to check yet"
    return parts.joinToString(". ")
}
