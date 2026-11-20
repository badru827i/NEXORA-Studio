package studio.nexora.app

data class SrtCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    val durationMs: Long get() = endMs - startMs
}

private val srtTimestampPattern = Regex("""(?:(\d+):)?(\d{2}):(\d{2})[,.](\d{3})""")

private fun parseSrtTimestamp(value: String): Long? {
    val match = srtTimestampPattern.find(value.trim()) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    val millis = match.groupValues[4].toLongOrNull() ?: return null
    if (minutes > 59L || seconds > 59L) return null
    return (((hours * 60L + minutes) * 60L + seconds) * 1_000L + millis)
}

fun parseSrt(content: String): List<SrtCue> {
    val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    return normalized.split(Regex("\n\\s*\n"))
        .mapNotNull { block ->
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) return@mapNotNull null
            val timing = lines[timingIndex].split("-->", limit = 2)
            if (timing.size != 2) return@mapNotNull null
            val start = parseSrtTimestamp(timing[0]) ?: return@mapNotNull null
            val end = parseSrtTimestamp(timing[1].substringBefore(' ').trim()) ?: return@mapNotNull null
            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (end <= start || text.isBlank()) return@mapNotNull null
            SrtCue(start, end, text.take(240))
        }
        .sortedBy { it.startMs }
}

fun TimelineDocument.addSubtitleCues(trackId: String, cues: List<SrtCue>): TimelineDocument =
    cues.fold(this) { document, cue ->
        document.addClip(
            trackId,
            TimelineClip(
                assetUri = "text://subtitle",
                label = cue.text,
                startMs = cue.startMs,
                durationMs = cue.durationMs,
                textRole = "SUBTITLE",
                textSizeScale = 0.042f,
                textPosition = "BOTTOM"
            )
        )
    }
