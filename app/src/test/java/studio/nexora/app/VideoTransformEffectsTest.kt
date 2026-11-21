package studio.nexora.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTransformEffectsTest {
    @Test
    fun cropZoomOneKeepsEntireFrame() {
        val bounds = normalizedCropBounds(1f)
        assertEquals(-1f, bounds.left, 0.0001f)
        assertEquals(1f, bounds.right, 0.0001f)
        assertEquals(-1f, bounds.bottom, 0.0001f)
        assertEquals(1f, bounds.top, 0.0001f)
    }

    @Test
    fun cropZoomTwoKeepsCenteredHalfFrame() {
        val bounds = normalizedCropBounds(2f)
        assertEquals(-0.5f, bounds.left, 0.0001f)
        assertEquals(0.5f, bounds.right, 0.0001f)
        assertEquals(-0.5f, bounds.bottom, 0.0001f)
        assertEquals(0.5f, bounds.top, 0.0001f)
    }

    @Test
    fun cropZoomIsClampedToSupportedRange() {
        assertEquals(normalizedCropBounds(2.5f), normalizedCropBounds(9f))
        assertEquals(normalizedCropBounds(1f), normalizedCropBounds(Float.NaN))
    }

    @Test
    fun rotationOnlyAllowsRightAngleValues() {
        assertEquals(0, safeRotationDegrees(0))
        assertEquals(90, safeRotationDegrees(90))
        assertEquals(180, safeRotationDegrees(180))
        assertEquals(270, safeRotationDegrees(270))
        assertEquals(0, safeRotationDegrees(45))
    }

    @Test
    fun keyframedTransformEvaluatesPositionScaleRotationAndZoom() {
        val clip = TimelineClip(
            id = "clip", assetUri = "video", label = "Shot", startMs = 0L, durationMs = 1_000L,
            keyframes = listOf(
                AnimationKeyframe(KeyframeProperty.POSITION_X, 0L, -0.5f),
                AnimationKeyframe(KeyframeProperty.POSITION_X, 1_000L, 0.5f),
                AnimationKeyframe(KeyframeProperty.SCALE_X, 0L, 1f),
                AnimationKeyframe(KeyframeProperty.SCALE_X, 1_000L, 3f),
                AnimationKeyframe(KeyframeProperty.ROTATION, 0L, 0f),
                AnimationKeyframe(KeyframeProperty.ROTATION, 1_000L, 180f),
                AnimationKeyframe(KeyframeProperty.CROP_ZOOM, 0L, 1f),
                AnimationKeyframe(KeyframeProperty.CROP_ZOOM, 1_000L, 2f)
            )
        )
        val value = evaluateKeyframedTransform(clip, 500L)
        assertEquals(0f, value.positionX, 0.0001f)
        assertEquals(2f, value.scaleX, 0.0001f)
        assertEquals(90f, value.rotationDegrees, 0.0001f)
        assertEquals(1.5f, value.cropZoom, 0.0001f)
    }

    @Test
    fun keyframedTransformUsesBaseValuesAndClampsUnsafeValues() {
        val clip = TimelineClip(
            id = "clip", assetUri = "video", label = "Shot", startMs = 0L, durationMs = 1_000L
        )
        val value = evaluateKeyframedTransform(clip, 500L, baseCropZoom = 1.8f, baseRotationDegrees = 90)
        assertEquals(1.8f, value.cropZoom, 0.0001f)
        assertEquals(90f, value.rotationDegrees, 0.0001f)
        assertEquals(1f, value.scaleX, 0.0001f)
        assertEquals(0f, value.positionY, 0.0001f)
    }

    @Test
    fun keyframeControlsUsePropertySpecificRangesAndDefaults() {
        assertEquals(0f..1f, keyframeValueRange(KeyframeProperty.OPACITY))
        assertEquals(-360f..360f, keyframeValueRange(KeyframeProperty.ROTATION))
        assertEquals(1f, defaultKeyframeValue(KeyframeProperty.SCALE_X), 0.0001f)
        assertEquals(1f, defaultKeyframeValue(KeyframeProperty.VOLUME), 0.0001f)
        assertEquals(0f, defaultKeyframeValue(KeyframeProperty.POSITION_X), 0.0001f)
    }

    @Test
    fun outputPresetsMapToShortSideSizes() {
        assertEquals(480, outputShortSide("480p"))
        assertEquals(720, outputShortSide("720p"))
        assertEquals(1080, outputShortSide("1080p"))
        assertEquals(null, outputShortSide("Original"))
    }
}
