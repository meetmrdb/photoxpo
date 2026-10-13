package app.photoxpo.core

import java.time.Duration

/** A Backup run copies about this much, then stops. */
const val RUN_SIZE_LIMIT_BYTES: Long = 10_000_000_000L

/** A Backup run keeps copying for about this long, then stops. */
val RUN_TIME_LIMIT: Duration = Duration.ofMinutes(45)

/** At or below this battery level, while not charging, a Backup run stops. There is no setting. */
const val LOW_BATTERY_PERCENT: Int = 15

/** The phone's battery right now. */
data class BatteryStatus(
    val percent: Int,
    val charging: Boolean,
)

/** Reads the phone's battery. Null means unknown, which never stops a run. Android feeds this from :app. */
fun interface BatteryInput {
    fun read(): BatteryStatus?
}

/** Safety stop, well before Android's 6 hour foreground-service limit. Ends as a normal [StopReason.TimeLimit]. */
val RUN_HARD_STOP: Duration = Duration.ofHours(5)

/**
 * Why a Backup run ended before every Original was Backed up. Each one is a normal result, never an error.
 * Later reasons (low battery, Android's time limit, Archive Drive full) are added here and in a [StopPolicy].
 */
enum class StopReason {
    SizeLimit,
    TimeLimit,
    LowBattery,
}

/** What the run knows when it is about to copy one more Original. */
data class RunProgress(
    val copiedBytes: Long,
    val elapsed: Duration,
    val nextFileBytes: Long,
    /** Null when the battery level is unknown. */
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
)

/**
 * The one place that decides a run should stop. The run asks before each new copy, so it always
 * finishes the current file and never leaves a half-copied one. Null means keep going.
 */
fun interface StopPolicy {
    fun reasonToStop(progress: RunProgress): StopReason?
}

class DefaultStopPolicy(
    private val maxCopiedBytes: Long = RUN_SIZE_LIMIT_BYTES,
    private val maxElapsed: Duration = RUN_TIME_LIMIT,
    private val hardStop: Duration = RUN_HARD_STOP,
) : StopPolicy {
    override fun reasonToStop(progress: RunProgress): StopReason? {
        val battery = progress.batteryPercent
        if (battery != null && battery <= LOW_BATTERY_PERCENT && !progress.charging) return StopReason.LowBattery
        if (progress.elapsed >= minOf(maxElapsed, hardStop)) return StopReason.TimeLimit
        // The first copy always goes ahead, so one Original bigger than the limit is not stuck forever.
        if (progress.copiedBytes > 0 && progress.copiedBytes + progress.nextFileBytes > maxCopiedBytes) {
            return StopReason.SizeLimit
        }
        return null
    }
}
