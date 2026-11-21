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
            listOf(TimelineTrack(id = "music", name = "Audio 1", type = TimelineTrackType.AUDIO, clips = listOf(clip), muted = true))
        )

        val restored = TimelineDocument.fromJson(original.toJson())
        assertEquals(original, restored)
        assertTrue(restored.tracks.single().clips.single().muted)
        assertTrue(restored.tracks.single().muted)
        assertFalse(restored.tracks.single().locked)
    }

    @Test
    fun animationKeyframesRoundTripAndClampToClipDuration() {
        val clip = TimelineClip(
            id = "animated", assetUri = "content://video/1", label = "Shot",
            startMs = 0L, durationMs = 5_000L,
            keyframes = listOf(
                AnimationKeyframe(KeyframeProperty.POSITION_X, 0L, 0.1f),
                AnimationKeyframe(KeyframeProperty.POSITION_X, 2_500L, 0.8f, KeyframeEasing.EASE_IN_OUT),
                AnimationKeyframe(KeyframeProperty.ROTATION, 5_000L, 90f, KeyframeEasing.EASE_OUT),
                AnimationKeyframe(KeyframeProperty.EXPOSURE, 8_000L, 0.5f, KeyframeEasing.HOLD),
                AnimationKeyframe(KeyframeProperty.HUE_BLUE, 1_200L, -20f)
            )
        )
        val document = TimelineDocument(
            listOf(TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO, clips = listOf(clip)))
        )
        val restored = TimelineDocument.fromJson(document.toJson()).tracks.single().clips.single()

        assertEquals(5, restored.keyframes.size)
        assertEquals(0L, restored.keyframes.first { it.property == KeyframeProperty.POSITION_X }.timeMs)
        assertEquals(2_500L, restored.keyframes.first { it.property == KeyframeProperty.POSITION_X && it.value > 0.5f }.timeMs)
        assertEquals(KeyframeEasing.EASE_IN_OUT, restored.keyframes.first { it.property == KeyframeProperty.POSITION_X && it.value > 0.5f }.easing)
        assertEquals(5_000L, restored.keyframes.first { it.property == KeyframeProperty.ROTATION }.timeMs)
        assertEquals(5_000L, restored.keyframes.first { it.property == KeyframeProperty.EXPOSURE }.timeMs)
        assertEquals(-20f, restored.keyframes.first { it.property == KeyframeProperty.HUE_BLUE }.value, 0.0001f)
    }

    @Test
    fun keyframeValuesInterpolateWithAllEasingModesAndClipLocalBounds() {
        val clip = TimelineClip(
            id = "animated", assetUri = "video", label = "Shot",
            startMs = 0L, durationMs = 1_000L,
            keyframes = listOf(
                AnimationKeyframe(KeyframeProperty.POSITION_X, 100L, 0f),
                AnimationKeyframe(KeyframeProperty.POSITION_X, 900L, 10f),
                AnimationKeyframe(KeyframeProperty.POSITION_Y, 100L, 0f, KeyframeEasing.EASE_IN),
                AnimationKeyframe(KeyframeProperty.POSITION_Y, 900L, 10f),
                AnimationKeyframe(KeyframeProperty.SCALE_X, 100L, 0f, KeyframeEasing.EASE_OUT),
                AnimationKeyframe(KeyframeProperty.SCALE_X, 900L, 10f),
                AnimationKeyframe(KeyframeProperty.SCALE_Y, 100L, 0f, KeyframeEasing.EASE_IN_OUT),
                AnimationKeyframe(KeyframeProperty.SCALE_Y, 900L, 10f),
                AnimationKeyframe(KeyframeProperty.ROTATION, 100L, 0f, KeyframeEasing.HOLD),
                AnimationKeyframe(KeyframeProperty.ROTATION, 900L, 10f)
            )
        )

        assertEquals(5f, clip.valueAt(KeyframeProperty.POSITION_X, 500L, -1f), 0.0001f)
        assertEquals(2.5f, clip.valueAt(KeyframeProperty.POSITION_Y, 500L, -1f), 0.0001f)
        assertEquals(7.5f, clip.valueAt(KeyframeProperty.SCALE_X, 500L, -1f), 0.0001f)
        assertEquals(5f, clip.valueAt(KeyframeProperty.SCALE_Y, 500L, -1f), 0.0001f)
        assertEquals(0f, clip.valueAt(KeyframeProperty.ROTATION, 500L, -1f), 0.0001f)
        assertEquals(0f, clip.valueAt(KeyframeProperty.POSITION_X, 0L, -1f), 0.0001f)
        assertEquals(10f, clip.valueAt(KeyframeProperty.POSITION_X, 1_000L, -1f), 0.0001f)
        assertEquals(-1f, clip.valueAt(KeyframeProperty.OPACITY, 500L, -1f), 0.0001f)
    }

    @Test
    fun keyframeEditingReplacesSameTimeMovesAndRemovesSafely() {
        val clip = TimelineClip(id = "clip", assetUri = "video", label = "Shot", startMs = 0L, durationMs = 4_000L)
        val document = TimelineDocument(listOf(
            TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO, clips = listOf(clip))
        ))
        val added = document.setKeyframe("video", "clip", AnimationKeyframe(KeyframeProperty.OPACITY, 1_000L, 0.5f))
        val replaced = added.setKeyframe("video", "clip", AnimationKeyframe(KeyframeProperty.OPACITY, 1_000L, 0.8f, KeyframeEasing.EASE_OUT))
        val moved = replaced.setKeyframe("video", "clip", AnimationKeyframe(KeyframeProperty.OPACITY, 9_000L, 0.9f))
        val keyframes = moved.tracks.single().clips.single().keyframes
        assertEquals(2, keyframes.size)
        assertEquals(4_000L, keyframes.last().timeMs)
        assertEquals(0.8f, keyframes.first().value, 0.0001f)
        assertEquals(KeyframeEasing.EASE_OUT, keyframes.first().easing)
        val removed = moved.removeKeyframe("video", "clip", KeyframeProperty.OPACITY, 1_000L)
        assertEquals(1, removed.tracks.single().clips.single().keyframes.size)
    }

    @Test
    fun textOverlayStyleAndSubtitleRoleRoundTripThroughJson() {
        val title = TimelineClip(
            id = "title", assetUri = "text://title", label = "Hello NEXORA",
            startMs = 1_000L, durationMs = 4_000L,
            textRole = "SUBTITLE", textColor = 0xFFFFD54F.toInt(),
            textBackgroundColor = 0, textSizeScale = 0.045f,
            textPosition = "TOP", textBold = false
        )
        val document = TimelineDocument(
            listOf(TimelineTrack(id = "text", name = "Text 1", type = TimelineTrackType.TEXT, clips = listOf(title)))
        )
        val restored = TimelineDocument.fromJson(document.toJson()).tracks.single().clips.single()

        assertEquals("SUBTITLE", restored.textRole)
        assertEquals(0xFFFFD54F.toInt(), restored.textColor)
        assertEquals(0, restored.textBackgroundColor)
        assertEquals(0.045f, restored.textSizeScale, 0.0001f)
        assertEquals("TOP", restored.textPosition)
        assertFalse(restored.textBold)
    }

    @Test
    fun parsesSrtCuesAndPreservesMultilineText() {
        val source = """1
00:00:01,200 --> 00:00:03,500
Hello NEXORA
Second line

2
00:00:04.000 --> 00:00:05.250
Subtitle two
"""
        val cues = parseSrt(source)
        assertEquals(2, cues.size)
        assertEquals(1_200L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("Hello NEXORA\nSecond line", cues[0].text)
        assertEquals(4_000L, cues[1].startMs)
    }

    @Test
    fun subtitleImportCreatesTimedTextTrackClips() {
        val document = TimelineDocument().addTrack(TimelineTrackType.TEXT)
        val track = document.tracks.last()
        val imported = document.addSubtitleCues(track.id, listOf(SrtCue(1_000L, 2_500L, "Test subtitle")))
        val cue = imported.tracks.last().clips.single()
        assertEquals("SUBTITLE", cue.textRole)
        assertEquals(1_000L, cue.startMs)
        assertEquals(1_500L, cue.durationMs)
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
    fun trimStartPreservesSourceTimingAndMinimumDuration() {
        val clip = TimelineClip(id = "clip", assetUri = "asset", label = "A", startMs = 1_000L, durationMs = 5_000L, sourceInMs = 500L)
        val document = TimelineDocument(listOf(TimelineTrack(id = "video", name = "Video", type = TimelineTrackType.VIDEO, clips = listOf(clip))))
        val trimmed = document.trimClipStart("video", "clip", 2_000L).tracks.single().clips.single()
        assertEquals(2_000L, trimmed.startMs)
        assertEquals(4_000L, trimmed.durationMs)
        assertEquals(1_500L, trimmed.sourceInMs)
        val earliest = document.trimClipStart("video", "clip", 0L).tracks.single().clips.single()
        assertEquals(500L, earliest.startMs)
        assertEquals(0L, earliest.sourceInMs)
    }

    @Test
    fun trimEndCannotExtendClipOrMakeItEmpty() {
        val clip = TimelineClip(id = "clip", assetUri = "asset", label = "A", startMs = 1_000L, durationMs = 5_000L)
        val document = TimelineDocument(listOf(TimelineTrack(id = "video", name = "Video", type = TimelineTrackType.VIDEO, clips = listOf(clip))))
        val shortened = document.trimClipEnd("video", "clip", 4_000L).tracks.single().clips.single()
        assertEquals(3_000L, shortened.durationMs)
        val minimum = document.trimClipEnd("video", "clip", 0L).tracks.single().clips.single()
        assertEquals(1L, minimum.durationMs)
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
    fun exportValidationRequiresAtLeastOneVideoClip() {
        val audio = TimelineTrack(
            id = "audio", name = "Audio 1", type = TimelineTrackType.AUDIO,
            clips = listOf(TimelineClip(assetUri = "content://audio/1", label = "Music", startMs = 0L, durationMs = 4_000L))
        )
        assertTrue(TimelineDocument(listOf(audio)).exportValidationError()?.contains("video clip") == true)
    }

    @Test
    fun exportValidationRejectsOverlappingClipsOnSameTrack() {
        val first = TimelineClip(id = "a", assetUri = "content://video/1", label = "A", startMs = 0L, durationMs = 3_000L)
        val second = TimelineClip(id = "b", assetUri = "content://video/2", label = "B", startMs = 2_000L, durationMs = 2_000L)
        val track = TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO, clips = listOf(first, second))
        assertTrue(TimelineDocument(listOf(track)).exportValidationError()?.contains("overlapping") == true)
    }

    @Test
    fun exportValidationAllowsGapsBetweenClips() {
        val first = TimelineClip(id = "a", assetUri = "content://video/1", label = "A", startMs = 0L, durationMs = 2_000L)
        val second = TimelineClip(id = "b", assetUri = "content://video/2", label = "B", startMs = 4_000L, durationMs = 2_000L)
        val track = TimelineTrack(id = "video", name = "Video 1", type = TimelineTrackType.VIDEO, clips = listOf(first, second))
        assertEquals(null, TimelineDocument(listOf(track)).exportValidationError())
    }

    @Test
    fun addedTracksUseReadableNames() {
        val document = TimelineDocument().addTrack(TimelineTrackType.AUDIO).addTrack(TimelineTrackType.TEXT)
        assertEquals(listOf("Video 1", "Audio 1", "Audio 2", "Text 1"), document.tracks.map { it.name })
    }
}
