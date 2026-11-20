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
    val match = srtTimestampPattern.matchEntire(value.trim()) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    val millis = match.groupValues[4].toLongOrNull() ?: return null
    if (minutes > 59L || seconds > 59L) return null
    return (((hours * 60L + minutes) * 60L + seconds) * 1_000L + millis)
}

fun parseSrt(content: String): List<SrtCue> {
    val lines = content.removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
    val cues = mutableListOf<SrtCue>()
    var index = 0

    while (index < lines.size) {
        while (index < lines.size && lines[index].isBlank()) index++
        if (index >= lines.size) break

        // Cue numbers are optional; find the next timing line.
        var timingIndex = index
        while (timingIndex < lines.size && !lines[timingIndex].contains("-->")) {
            timingIndex++
        }
        if (timingIndex >= lines.size) break

        val timing = lines[timingIndex].split("-->", limit = 2)
        if (timing.size != 2) {
            index = timingIndex + 1
            continue
        }

        val start = parseSrtTimestamp(timing[0])
        val endToken = timing[1].trim().split(Regex("""\s+"""), limit = 2).firstOrNull().orEmpty()
        val end = parseSrtTimestamp(endToken)

        val textLines = mutableListOf<String>()
        index = timingIndex + 1
        while (index < lines.size && lines[index].isNotBlank()) {
            textLines += lines[index].trimEnd()
            index++
        }
        val cueText = textLines.joinToString("\n").trim()
        if (start != null && end != null && end > start && cueText.isNotBlank()) {
            cues += SrtCue(start, end, cueText.take(240))
        }
    }
    return cues.sortedBy { it.startMs }
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
}
