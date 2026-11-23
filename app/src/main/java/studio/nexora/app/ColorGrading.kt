package studio.nexora.app

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbAdjustment

data class ColorGradeSettings(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val temperature: Float = 0f
) {
    fun sanitized() = copy(
        brightness = safe(brightness).coerceIn(-1f, 1f),
        contrast = safe(contrast).coerceIn(-1f, 1f),
        temperature = safe(temperature).coerceIn(-1f, 1f)
    )

    companion object {
        private fun safe(value: Float) = if (value.isFinite()) value else 0f
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun buildColorGradeEffects(settings: ColorGradeSettings): List<Effect> {
    val grade = settings.sanitized()
    val effects = mutableListOf<Effect>()
    if (grade.brightness != 0f) effects += Brightness(grade.brightness)
    if (grade.contrast != 0f) effects += Contrast(grade.contrast)
    if (grade.temperature != 0f) {
        val warm = grade.temperature
        effects += RgbAdjustment.Builder()
            .setRedScale(1f + 0.18f * warm.coerceAtLeast(0f))
            .setGreenScale(1f - 0.025f * kotlin.math.abs(warm))
            .setBlueScale(1f + 0.18f * (-warm).coerceAtLeast(0f))
            .build()
    }
    return effects
}
