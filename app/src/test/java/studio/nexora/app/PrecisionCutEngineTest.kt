package studio.nexora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrecisionCutEngineTest {
    @Test fun splitsIntoExactOneSecondParts() {
        assertEquals(
            listOf(CutRange(0, 1_000), CutRange(1_000, 2_000), CutRange(2_000, 3_000)),
            PrecisionCutEngine.splitAtInterval(0, 3_000)
        )
    }

    @Test fun keepsFinalPartialSegment() {
        assertEquals(
            listOf(CutRange(500, 1_500), CutRange(1_500, 2_500), CutRange(2_500, 2_750)),
            PrecisionCutEngine.splitAtInterval(500, 2_750)
        )
    }

    @Test fun keepOneSecondFromPlayhead() {
        assertEquals(CutRange(1_250, 2_250), PrecisionCutEngine.keepWindow(0, 5_000, 1_250))
    }

    @Test fun keepWindowClampsAtClipEnd() {
        assertEquals(CutRange(2_500, 3_000), PrecisionCutEngine.keepWindow(0, 3_000, 2_500))
        assertNull(PrecisionCutEngine.keepWindow(0, 3_000, 3_000))
    }

    @Test fun rejectsNonPositiveIntervalAndDuration() {
        try {
            PrecisionCutEngine.splitAtInterval(0, 2_000, 0)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            PrecisionCutEngine.keepWindow(0, 2_000, 0, 0)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
