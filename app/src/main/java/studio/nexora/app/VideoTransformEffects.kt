package studio.nexora.app

import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation

data class NormalizedCropBounds(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float
)

fun normalizedCropBounds(zoom: Float): NormalizedCropBounds {
    val safeZoom = if (zoom.isFinite()) zoom.coerceIn(1f, 2.5f) else 1f
    val halfExtent = 1f / safeZoom
    return NormalizedCropBounds(-halfExtent, halfExtent, -halfExtent, halfExtent)
}

fun safeRotationDegrees(degrees: Int): Int =
    if (degrees in listOf(0, 90, 180, 270)) degrees else 0

fun outputShortSide(preset: String): Int? = when (preset) {
    "480p" -> 480
    "720p" -> 720
    "1080p" -> 1080
    else -> null
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun buildVideoEffects(
    cropZoom: Float,
    rotationDegrees: Int,
    outputPreset: String,
    inputShortSide: Int? = null,
    colorGrade: ColorGradeSettings = ColorGradeSettings()
): List<Effect> {
    val effects = mutableListOf<Effect>()
    val rotation = safeRotationDegrees(rotationDegrees)
    if (rotation != 0) {
        effects += ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(rotation.toFloat())
            .build()
    }

    val bounds = normalizedCropBounds(cropZoom)
    if (cropZoom.isFinite() && cropZoom > 1.001f) {
        effects += Crop(bounds.left, bounds.right, bounds.bottom, bounds.top)
    }

    effects += buildColorGradeEffects(colorGrade)

    val targetShortSide = outputShortSide(outputPreset)
    if (targetShortSide != null) {
        val safeTarget = if (inputShortSide != null && inputShortSide > 0) {
            minOf(targetShortSide, inputShortSide)
        } else {
            targetShortSide
        }
        effects += Presentation.createForShortSide(safeTarget)
    }
    return effects
}
