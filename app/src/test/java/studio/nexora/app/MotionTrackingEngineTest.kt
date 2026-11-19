package studio.nexora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTrackingEngineTest {
    @Test
    fun followsTexturedTargetAcrossTranslatedFrames() {
        val frames = listOf(
            syntheticFrame(0L, 20),
            syntheticFrame(500L, 26),
            syntheticFrame(1_000L, 32),
            syntheticFrame(1_500L, 38)
        )
        val result = MotionTrackingEngine.trackFrames(
            frames,
            TrackingRegion(centerX = 0.30f, centerY = 0.40f, width = 0.20f, height = 0.20f)
        )

        assertEquals(4, result.points.size)
        assertTrue(result.points.last().centerX > result.points.first().centerX)
        assertTrue(result.averageConfidence > 0.8f)
    }

    @Test
    fun stopsWhenTargetDisappears() {
        val first = syntheticFrame(0L, 20)
        val blank = TrackingFrame(500L, 100, 80, ByteArray(8_000) { 24 })
        val result = MotionTrackingEngine.trackFrames(
            listOf(first, blank),
            TrackingRegion(centerX = 0.30f, centerY = 0.40f, width = 0.20f, height = 0.20f)
        )

        assertEquals(1, result.points.size)
        assertEquals(500L, result.lostAtMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFeaturelessSelection() {
        val blank = TrackingFrame(0L, 100, 80, ByteArray(8_000) { 24 })
        MotionTrackingEngine.trackFrames(
            listOf(blank),
            TrackingRegion(centerX = 0.5f, centerY = 0.5f, width = 0.2f, height = 0.2f)
        )
    }

    private fun syntheticFrame(timestampMs: Long, targetLeft: Int): TrackingFrame {
        val width = 100
        val height = 80
        val pixels = ByteArray(width * height) { 24 }
        val targetTop = 24
        for (y in 0 until 16) for (x in 0 until 20) {
            // Stable high-frequency pattern gives the tracker a distinctive target signature.
            pixels[(targetTop + y) * width + targetLeft + x] = (70 + ((x * 17 + y * 29 + x * y * 3) % 170)).toByte()
        }
        return TrackingFrame(timestampMs, width, height, pixels)
    }
}
