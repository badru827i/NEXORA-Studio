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
    fun outputPresetsMapToShortSideSizes() {
        assertEquals(480, outputShortSide("480p"))
        assertEquals(720, outputShortSide("720p"))
        assertEquals(1080, outputShortSide("1080p"))
        assertEquals(null, outputShortSide("Original"))
    }
}
