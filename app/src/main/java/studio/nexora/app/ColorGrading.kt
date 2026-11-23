package studio.nexora.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment

data class ColorGradeSettings(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val hue: Float = 0f,
    val temperature: Float = 0f
) {
    fun sanitized() = copy(
        brightness = finiteOrZero(brightness).coerceIn(-1f, 1f),
        contrast = finiteOrZero(contrast).coerceIn(-1f, 1f),
        saturation = finiteOrZero(saturation).coerceIn(-100f, 100f),
        hue = finiteOrZero(hue).coerceIn(-180f, 180f),
        temperature = finiteOrZero(temperature).coerceIn(-1f, 1f)
    )

    companion object {
        private fun finiteOrZero(value: Float) = if (value.isFinite()) value else 0f
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun buildColorGradeEffects(settings: ColorGradeSettings): List<Effect> {
    val grade = settings.sanitized()
    val effects = mutableListOf<Effect>()
    if (grade.brightness != 0f) effects += Brightness(grade.brightness)
    if (grade.contrast != 0f) effects += Contrast(grade.contrast)
    if (grade.saturation != 0f || grade.hue != 0f) {
        effects += HslAdjustment.Builder()
            .adjustHue(grade.hue)
            .adjustSaturation(grade.saturation)
            .build()
    }
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

@Composable
fun ColorGradePanel(settings: ColorGradeSettings, onChange: (ColorGradeSettings) -> Unit) {
    val grade = settings.sanitized()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GradeSlider("Brightness", grade.brightness, -1f..1f, { onChange(grade.copy(brightness = it)) }, "%.2f")
        GradeSlider("Contrast", grade.contrast, -1f..1f, { onChange(grade.copy(contrast = it)) }, "%.2f")
        GradeSlider("Saturation", grade.saturation, -100f..100f, { onChange(grade.copy(saturation = it)) }, "%.0f")
        GradeSlider("Hue", grade.hue, -180f..180f, { onChange(grade.copy(hue = it)) }, "%.0f°")
        GradeSlider("Temperature", grade.temperature, -1f..1f, { onChange(grade.copy(temperature = it)) }, "%.2f")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onChange(ColorGradeSettings()) }) { Text("Reset color") }
        }
    }
}

@Composable
private fun GradeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    format: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(String.format(java.util.Locale.US, format, value))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
