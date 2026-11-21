package studio.nexora.app

/**
 * Frame-independent precision cutting helpers.
 * All public times are milliseconds; ranges are half-open [startMs, endMs).
 */
data class CutRange(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Splits a clip into consecutive ranges of [intervalMs], keeping the final partial range. */
object PrecisionCutEngine {
    fun splitAtInterval(
        clipStartMs: Long,
        clipEndMs: Long,
        intervalMs: Long = 1_000L
    ): List<CutRange> {
        require(intervalMs > 0L) { "intervalMs must be positive" }
        if (clipEndMs <= clipStartMs) return emptyList()

        val ranges = ArrayList<CutRange>()
        var cursor = clipStartMs
        while (cursor < clipEndMs) {
            // Avoid overflow while preserving the exact clip end.
            val remaining = clipEndMs - cursor
            val next = if (remaining <= intervalMs) clipEndMs else cursor + intervalMs
            ranges += CutRange(cursor, next)
            cursor = next
        }
        return ranges
    }

    /** Returns a one-second (or requested duration) selection starting at the playhead. */
    fun keepWindow(
        clipStartMs: Long,
        clipEndMs: Long,
        playheadMs: Long,
        durationMs: Long = 1_000L
    ): CutRange? {
        require(durationMs > 0L) { "durationMs must be positive" }
        if (clipEndMs <= clipStartMs) return null
        val start = playheadMs.coerceIn(clipStartMs, clipEndMs)
        if (start == clipEndMs) return null
        val available = clipEndMs - start
        val end = if (available <= durationMs) clipEndMs else start + durationMs
        return CutRange(start, end)
    }

    /** Converts a timeline time to clip-local time, clamped to the clip bounds. */
    fun toClipLocalTime(timelineMs: Long, clipStartMs: Long, clipEndMs: Long): Long {
        if (clipEndMs <= clipStartMs) return 0L
        return (timelineMs - clipStartMs).coerceIn(0L, clipEndMs - clipStartMs)
    }
}
