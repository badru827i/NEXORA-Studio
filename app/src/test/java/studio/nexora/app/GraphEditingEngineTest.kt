package studio.nexora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphEditingEngineTest {
    @Test fun linearCurveInterpolatesBetweenPoints() {
        val curve = GraphCurve(listOf(
            GraphKeyframe(0f, 0f, GraphInterpolation.LINEAR),
            GraphKeyframe(1f, 10f, GraphInterpolation.LINEAR)
        ))
        assertEquals(5f, curve.valueAt(0.5f), 0.001f)
    }

    @Test fun holdCurveKeepsPreviousValueUntilNextPoint() {
        val curve = GraphCurve(listOf(
            GraphKeyframe(0f, 2f, GraphInterpolation.HOLD),
            GraphKeyframe(1f, 8f)
        ))
        assertEquals(2f, curve.valueAt(0.8f), 0.001f)
    }

    @Test fun duplicateTimesAreCollapsedAndValuesRemainFinite() {
        val curve = GraphCurve(listOf(
            GraphKeyframe(0.5f, 1f),
            GraphKeyframe(0.5f, 3f),
            GraphKeyframe(Float.NaN, 9f)
        ))
        assertEquals(3f, curve.valueAt(0.5f), 0.001f)
    }

    @Test fun blendModesRespectOpacityAndClampChannels() {
        assertEquals(0.25f, blendChannel(0.25f, 1f, 0f, BlendMode.ADD), 0.001f)
        assertEquals(1f, blendChannel(0.8f, 0.8f, 1f, BlendMode.ADD), 0.001f)
        assertTrue(blendChannel(0.5f, 0.5f, 1f, BlendMode.MULTIPLY) in 0f..1f)
    }

    @Test fun speedRampClampsUnsafeValues() {
        val ramp = SpeedRamp(GraphCurve.constant(20f))
        assertEquals(8f, ramp.speedAt(0.5f), 0.001f)
    }

    @Test fun colorGradeSanitizesOutOfRangeValues() {
        val grade = ColorGrade(exposureEv = 99f, saturation = -4f).safe()
        assertEquals(5f, grade.exposureEv, 0.001f)
        assertEquals(0f, grade.saturation, 0.001f)
    }
}
