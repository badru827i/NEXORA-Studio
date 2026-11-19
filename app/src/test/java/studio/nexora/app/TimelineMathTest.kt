package studio.nexora.app
import org.junit.Assert.assertEquals
import org.junit.Test
class TimelineMathTest {
    @Test fun trimStartStaysBeforeEnd() { assertEquals(49f, safeTrimStart(90f, 50f)) }
    @Test fun trimEndStaysAfterStart() { assertEquals(51f, safeTrimEnd(10f, 50f)) }
    @Test fun speedIsClamped() {
        assertEquals(0.25f, safeSpeed(0f))
        assertEquals(3f, safeSpeed(8f))
    }
}
