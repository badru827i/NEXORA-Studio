package studio.nexora.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TimelineTrackType { VIDEO, AUDIO, TEXT }

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val assetUri: String,
    val label: String,
    val startMs: Long,
    val durationMs: Long,
    val sourceInMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val textRole: String = "TITLE",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textBackgroundColor: Int = 0x99000000.toInt(),
    val textSizeScale: Float = 0.06f,
    val textPosition: String = "BOTTOM",
    val textBold: Boolean = true
) {
    val endMs: Long get() = startMs + durationMs
}

data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TimelineTrackType,
    val clips: List<TimelineClip> = emptyList(),
    val muted: Boolean = false,
    val locked: Boolean = false
)

data class TimelineDocument(
    val tracks: List<TimelineTrack> = listOf(
        TimelineTrack(name = "Video 1", type = TimelineTrackType.VIDEO),
        TimelineTrack(name = "Audio 1", type = TimelineTrackType.AUDIO)
    )
) {
    val durationMs: Long
        get() = tracks.flatMap { it.clips }.maxOfOrNull { it.endMs } ?: 0L

    fun exportValidationError(): String? {
        val hasVideo = tracks.any { track ->
            track.type == TimelineTrackType.VIDEO && track.clips.any {
                it.assetUri.isNotBlank() && !it.assetUri.startsWith("text://") && it.durationMs > 0L
            }
        }
        if (!hasVideo) return "Add at least one video clip to a Video track before exporting."

        tracks.filter { it.type == TimelineTrackType.VIDEO || it.type == TimelineTrackType.AUDIO }
            .forEach { track ->
                var cursorMs = 0L
                track.clips.sortedBy { it.startMs }.forEach { clip ->
                    if (clip.durationMs <= 0L) return "Track ${track.name} contains an empty clip."
                    if (clip.startMs < cursorMs) {
                        return "Track ${track.name} has overlapping clips. Move clips so they do not overlap before export."
                    }
                    cursorMs = clip.endMs
                }
            }
        return null
    }

    fun addTrack(type: TimelineTrackType): TimelineDocument {
        val index = tracks.count { it.type == type } + 1
        val prefix = when (type) {
            TimelineTrackType.VIDEO -> "Video"
            TimelineTrackType.AUDIO -> "Audio"
            TimelineTrackType.TEXT -> "Text"
        }
        return copy(tracks = tracks + TimelineTrack(name = "$prefix $index", type = type))
    }

    fun addClip(trackId: String, clip: TimelineClip): TimelineDocument = copy(
        tracks = tracks.map { track ->
            if (track.id == trackId && !track.locked) {
                track.copy(clips = (track.clips + clip).sortedBy { it.startMs })
            } else track
        }
    )

    fun moveClip(trackId: String, clipId: String, newStartMs: Long): TimelineDocument = copy(
        tracks = tracks.map { track ->
            if (track.id != trackId || track.locked) track
            else track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(startMs = newStartMs.coerceAtLeast(0L)) else clip
            }.sortedBy { it.startMs })
        }
    )

    fun trimClipStart(trackId: String, clipId: String, proposedStartMs: Long): TimelineDocument = copy(
        tracks = tracks.map { track ->
            if (track.id != trackId || track.locked) track else track.copy(clips = track.clips.map { clip ->
                if (clip.id != clipId) clip else {
                    val earliestStart = (clip.startMs - clip.sourceInMs).coerceAtLeast(0L)
                    val newStart = proposedStartMs.coerceIn(earliestStart, clip.endMs - 1L)
                    val delta = newStart - clip.startMs
                    clip.copy(startMs = newStart, durationMs = clip.endMs - newStart, sourceInMs = clip.sourceInMs + delta)
                }
            }.sortedBy { it.startMs })
        }
    )

    fun trimClipEnd(trackId: String, clipId: String, proposedEndMs: Long): TimelineDocument = copy(
        tracks = tracks.map { track ->
            if (track.id != trackId || track.locked) track else track.copy(clips = track.clips.map { clip ->
                if (clip.id != clipId) clip else clip.copy(durationMs = proposedEndMs.coerceIn(clip.startMs + 1L, clip.endMs) - clip.startMs)
            }.sortedBy { it.startMs })
        }
    )

    fun snapPosition(
        trackId: String,
        clipId: String,
        proposedStartMs: Long,
        thresholdMs: Long = 250L
    ): Long {
        val track = tracks.firstOrNull { it.id == trackId } ?: return proposedStartMs.coerceAtLeast(0L)
        val clip = track.clips.firstOrNull { it.id == clipId } ?: return proposedStartMs.coerceAtLeast(0L)
        val proposed = proposedStartMs.coerceAtLeast(0L)
        val candidates = track.clips.asSequence()
            .filterNot { it.id == clipId }
            .flatMap { other -> sequenceOf((other.startMs - clip.durationMs).coerceAtLeast(0L), other.endMs) }
            .plus(sequenceOf(0L))
        return candidates
            .map { candidate -> candidate to kotlin.math.abs(candidate - proposed) }
            .filter { (_, distance) -> distance <= thresholdMs.coerceAtLeast(0L) }
            .minByOrNull { it.second }
            ?.first ?: proposed
    }

    fun moveClipMagnetic(
        trackId: String,
        clipId: String,
        proposedStartMs: Long,
        thresholdMs: Long = 250L
    ): TimelineDocument = moveClip(trackId, clipId, snapPosition(trackId, clipId, proposedStartMs, thresholdMs))

    fun rippleRemoveClip(trackId: String, clipId: String): TimelineDocument {
        val track = tracks.firstOrNull { it.id == trackId && !it.locked } ?: return this
        val removed = track.clips.firstOrNull { it.id == clipId } ?: return this
        return copy(tracks = tracks.map { current ->
            if (current.id != trackId) current else current.copy(
                clips = current.clips
                    .filterNot { it.id == clipId }
                    .map { clip ->
                        if (clip.startMs >= removed.endMs) clip.copy(startMs = clip.startMs - removed.durationMs)
                        else clip
                    }
                    .sortedBy { it.startMs }
            )
        })
    }

    fun removeClip(trackId: String, clipId: String): TimelineDocument = copy(
        tracks = tracks.map { track ->
            if (track.id == trackId && !track.locked) track.copy(clips = track.clips.filterNot { it.id == clipId })
            else track
        }
    )

    fun splitClip(trackId: String, clipId: String, splitAtMs: Long): TimelineDocument {
        val track = tracks.firstOrNull { it.id == trackId && !it.locked } ?: return this
        val clip = track.clips.firstOrNull { it.id == clipId } ?: return this
        if (splitAtMs <= clip.startMs || splitAtMs >= clip.endMs) return this
        val leftDuration = splitAtMs - clip.startMs
        val rightDuration = clip.durationMs - leftDuration
        val left = clip.copy(durationMs = leftDuration)
        val right = clip.copy(
            id = UUID.randomUUID().toString(),
            startMs = splitAtMs,
            sourceInMs = clip.sourceInMs + leftDuration,
            durationMs = rightDuration
        )
        return copy(tracks = tracks.map { item ->
            if (item.id == trackId) item.copy(clips = item.clips.flatMap { current ->
                if (current.id == clipId) listOf(left, right) else listOf(current)
            }.sortedBy { it.startMs }) else item
        })
    }

    fun toJson(): String = JSONObject().put("tracks", JSONArray().apply {
        tracks.forEach { track ->
            put(JSONObject()
                .put("id", track.id)
                .put("name", track.name)
                .put("type", track.type.name)
                .put("muted", track.muted)
                .put("locked", track.locked)
                .put("clips", JSONArray().apply {
                    track.clips.forEach { clip ->
                        put(JSONObject()
                            .put("id", clip.id)
                            .put("assetUri", clip.assetUri)
                            .put("label", clip.label)
                            .put("startMs", clip.startMs)
                            .put("durationMs", clip.durationMs)
                            .put("sourceInMs", clip.sourceInMs)
                            .put("volume", clip.volume.coerceIn(0f, 1f).toDouble())
                            .put("muted", clip.muted)
                            .put("textRole", clip.textRole)
                            .put("textColor", clip.textColor)
                            .put("textBackgroundColor", clip.textBackgroundColor)
                            .put("textSizeScale", clip.textSizeScale.coerceIn(0.025f, 0.12f).toDouble())
                            .put("textPosition", clip.textPosition)
                            .put("textBold", clip.textBold))
                    }
                }))
        }
    }).toString()

    companion object {
        fun fromJson(json: String): TimelineDocument = runCatching {
            val array = JSONObject(json).optJSONArray("tracks") ?: JSONArray()
            val tracks = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val type = runCatching { TimelineTrackType.valueOf(item.optString("type")) }
                    .getOrDefault(TimelineTrackType.VIDEO)
                val clipsJson = item.optJSONArray("clips") ?: JSONArray()
                val clips = (0 until clipsJson.length()).mapNotNull { clipIndex ->
                    val clip = clipsJson.optJSONObject(clipIndex) ?: return@mapNotNull null
                    val duration = clip.optLong("durationMs", 0L)
                    if (duration <= 0L) return@mapNotNull null
                    TimelineClip(
                        id = clip.optString("id").ifBlank { UUID.randomUUID().toString() },
                        assetUri = clip.optString("assetUri"),
                        label = clip.optString("label", "Clip"),
                        startMs = clip.optLong("startMs", 0L).coerceAtLeast(0L),
                        durationMs = duration,
                        sourceInMs = clip.optLong("sourceInMs", 0L).coerceAtLeast(0L),
                        volume = clip.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
                        muted = clip.optBoolean("muted", false),
                        textRole = clip.optString("textRole", "TITLE").takeIf { it in listOf("TITLE", "SUBTITLE") } ?: "TITLE",
                        textColor = clip.optInt("textColor", 0xFFFFFFFF.toInt()),
                        textBackgroundColor = clip.optInt("textBackgroundColor", 0x99000000.toInt()),
                        textSizeScale = clip.optDouble("textSizeScale", 0.06).toFloat().coerceIn(0.025f, 0.12f),
                        textPosition = clip.optString("textPosition", "BOTTOM").takeIf { it in listOf("TOP", "CENTER", "BOTTOM") } ?: "BOTTOM",
                        textBold = clip.optBoolean("textBold", true)
                    )
                }
                TimelineTrack(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = item.optString("name", "Track"),
                    type = type,
                    clips = clips.sortedBy { it.startMs },
                    muted = item.optBoolean("muted", false),
                    locked = item.optBoolean("locked", false)
                )
            }
            TimelineDocument(if (tracks.isEmpty()) TimelineDocument().tracks else tracks)
        }.getOrDefault(TimelineDocument())
    }
}
