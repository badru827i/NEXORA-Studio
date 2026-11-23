package studio.nexora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorGradeSettingsTest {
    @Test
    fun defaultGradeDoesNotAddEffects() {
        assertTrue(buildColorGradeEffects(ColorGradeSettings()).isEmpty())
    }

    @Test
    fun gradeValuesAreClampedToSupportedRanges() {
        val grade = ColorGradeSettings(
            brightness = 4f,
            contrast = -4f,
            temperature = Float.NaN
        ).sanitized()

        assertEquals(1f, grade.brightness)
        assertEquals(-1f, grade.contrast)
        assertEquals(0f, grade.temperature)
    }

    @Test
    fun enabledGradeControlsProduceVideoEffects() {
        val effects = buildColorGradeEffects(
            ColorGradeSettings(brightness = 0.2f, contrast = 0.3f, temperature = -0.5f)
        )
        assertEquals(3, effects.size)
    }
}
