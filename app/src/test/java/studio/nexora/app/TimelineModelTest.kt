package studio.nexora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineModelTest {
    @Test
    fun splitClipPreservesSourceTimingAndTotalDuration() {
        val track = TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO)
        val original = TimelineClip(
            id = "clip", assetUri = "content://media/1", label = "Shot",
            startMs = 1_000L, durationMs = 8_000L, sourceInMs = 500L
        )
        val result = TimelineDocument(listOf(track.copy(clips = listOf(original))))
            .splitClip("video", "clip", 4_000L)

        val clips = result.tracks.single().clips
        assertEquals(2, clips.size)
        assertEquals(3_000L, clips[0].durationMs)
        assertEquals(5_000L, clips[1].durationMs)
        assertEquals(3_500L, clips[1].sourceInMs)
        assertEquals(9_000L, result.durationMs)
    }

    @Test
    fun lockedTrackCannotMoveOrRemoveClips() {
        val clip = TimelineClip(id = "clip", assetUri = "asset", label = "A", startMs = 0L, durationMs = 2_000L)
        val track = TimelineTrack(id = "audio", name = "Audio 1", type = TimelineTrackType.AUDIO, clips = listOf(clip), locked = true)
        val document = TimelineDocument(listOf(track))

        assertEquals(0L, document.moveClip("audio", "clip", 900L).tracks.single().clips.single().startMs)
        assertEquals(1, document.removeClip("audio", "clip").tracks.single().clips.size)
        assertEquals(1, document.splitClip("audio", "clip", 1_000L).tracks.single().clips.size)
    }

    @Test
    fun timelineRoundTripsThroughJson() {
        val clip = TimelineClip(id = "clip", assetUri = "content://asset", label = "Music", startMs = 200L, durationMs = 5_000L, volume = 0.65f, muted = true)
        val original = TimelineDocument(
            listOf(TimelineTrack(id = "music", name = "Audio 1", type = TimelineTrackType.AUDIO, clips = listOf(clip), muted = false))
        )

        val restored = TimelineDocument.fromJson(original.toJson())
        assertEquals(original, restored)
        assertTrue(restored.tracks.single().clips.single().muted)
        assertFalse(restored.tracks.single().locked)
    }

    @Test
    fun magneticMoveSnapsClipEdgeToNearbyClip() {
        val first = TimelineClip(id = "a", assetUri = "a", label = "A", startMs = 0L, durationMs = 2_000L)
        val second = TimelineClip(id = "b", assetUri = "b", label = "B", startMs = 3_000L, durationMs = 1_000L)
        val document = TimelineDocument(
            listOf(TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO, clips = listOf(first, second)))
        )

        val moved = document.moveClipMagnetic("video", "a", 850L, thresholdMs = 200L)
        assertEquals(1_000L, moved.tracks.single().clips.first { it.id == "a" }.startMs)
    }

    @Test
    fun rippleDeleteClosesGapWithoutChangingOtherClipDurations() {
        val first = TimelineClip(id = "a", assetUri = "a", label = "A", startMs = 0L, durationMs = 2_000L)
        val removed = TimelineClip(id = "b", assetUri = "b", label = "B", startMs = 2_000L, durationMs = 1_500L)
        val last = TimelineClip(id = "c", assetUri = "c", label = "C", startMs = 5_000L, durationMs = 2_000L)
        val document = TimelineDocument(
            listOf(TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO, clips = listOf(first, removed, last)))
        )

        val result = document.rippleRemoveClip("video", "b")
        val clips = result.tracks.single().clips
        assertEquals(listOf("a", "c"), clips.map { it.id })
        assertEquals(3_500L, clips.last().startMs)
        assertEquals(2_000L, clips.last().durationMs)
        assertEquals(5_500L, result.durationMs)
    }

    @Test
    fun addedTracksUseReadableNames() {
        val document = TimelineDocument().addTrack(TimelineTrackType.AUDIO).addTrack(TimelineTrackType.TEXT)
        assertEquals(listOf("Video 1", "Audio 1", "Audio 2", "Text 1"), document.tracks.map { it.name })
    }
}
