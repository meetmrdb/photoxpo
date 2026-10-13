package app.photoxpo.core

/**
 * How a finished Backup run ended, as written in its Manifest footer and shown in Backup history.
 * [Finished] means every Original was Backed up; [Stopped] carries the [StopReason].
 */
sealed interface RunEnd {
    data object Finished : RunEnd

    data class Stopped(val reason: StopReason) : RunEnd

    /** The footer value. Stable across app versions: never rename an existing one. */
    val wire: String
        get() = when (this) {
            Finished -> "finished"
            is Stopped -> reason.wire()
        }

    companion object {
        /** Null when [wire] is missing or from a newer app, so the run still shows. */
        fun fromWire(wire: String?): RunEnd? {
            if (wire == null) return null
            if (wire == Finished.wire) return Finished
            return StopReason.entries.firstOrNull { it.wire() == wire }?.let(::Stopped)
        }
    }
}

/** Adding a [StopReason] fails to compile here until it has a footer value and plain words. */
private fun StopReason.wire(): String = when (this) {
    StopReason.SizeLimit -> "size-limit"
    StopReason.TimeLimit -> "time-limit"
    StopReason.LowBattery -> "low-battery"
    StopReason.DriveFull -> "drive-full"
}

/** The plain-words line Backup history shows for a run. */
fun runEndWords(end: RunEnd): String = when (end) {
    RunEnd.Finished -> "Finished: everything new was backed up"
    is RunEnd.Stopped -> when (end.reason) {
        StopReason.SizeLimit -> "Stopped at the size limit"
        StopReason.TimeLimit -> "Stopped at the time limit"
        StopReason.LowBattery -> "Paused at $LOW_BATTERY_PERCENT% battery"
        StopReason.DriveFull -> "Your Archive Drive is full"
    }
}
