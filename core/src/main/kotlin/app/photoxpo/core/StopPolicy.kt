package app.photoxpo.core

import java.time.Duration

/** A Backup run copies about this much, then stops. */
const val RUN_SIZE_LIMIT_BYTES: Long = 10_000_000_000L

/** A Backup run keeps copying for about this long, then stops. */
val RUN_TIME_LIMIT: Duration = Duration.ofMinutes(45)

/**
 * Why a Backup run ended before every Original was Backed up. Each one is a normal result, never an error.
 * Later reasons (low battery, Android's time limit, Archive Drive full) are added here and in a [StopPolicy].
 */
enum class StopReason {
    SizeLimit,
    TimeLimit,
}

/** What the run knows when it is about to copy one more Original. */
data class RunProgress(
    val copiedBytes: Long,
    val elapsed: Duration,
    val nextFileBytes: Long,
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
) : StopPolicy {
    override fun reasonToStop(progress: RunProgress): StopReason? {
        if (progress.elapsed >= maxElapsed) return StopReason.TimeLimit
        // The first copy always goes ahead, so one Original bigger than the limit is not stuck forever.
        if (progress.copiedBytes > 0 && progress.copiedBytes + progress.nextFileBytes > maxCopiedBytes) {
            return StopReason.SizeLimit
        }
        return null
    }
}
