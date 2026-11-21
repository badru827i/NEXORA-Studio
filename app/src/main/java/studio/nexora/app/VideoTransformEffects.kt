package studio.nexora.app

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation

data class NormalizedCropBounds(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float
)

data class KeyframedTransform(
    val positionX: Float,
    val positionY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val cropZoom: Float
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

fun keyframeValueRange(property: KeyframeProperty): ClosedFloatingPointRange<Float> = when (property) {
    KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y -> -1f..1f
    KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y -> 0.1f..5f
    KeyframeProperty.ROTATION -> -360f..360f
    KeyframeProperty.OPACITY, KeyframeProperty.VOLUME -> 0f..1f
    KeyframeProperty.CROP_ZOOM -> 1f..2.5f
    KeyframeProperty.EXPOSURE -> -4f..4f
    KeyframeProperty.CONTRAST, KeyframeProperty.SATURATION -> 0f..2f
    KeyframeProperty.TEMPERATURE, KeyframeProperty.TINT -> -100f..100f
    else -> if (property.name.startsWith("HUE_")) -180f..180f else -100f..100f
}

fun defaultKeyframeValue(property: KeyframeProperty): Float = when (property) {
    KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y,
    KeyframeProperty.CROP_ZOOM, KeyframeProperty.CONTRAST,
    KeyframeProperty.SATURATION, KeyframeProperty.OPACITY,
    KeyframeProperty.VOLUME -> 1f
    else -> 0f
}

fun evaluateKeyframedTransform(
    clip: TimelineClip,
    timeMs: Long,
    baseCropZoom: Float = 1f,
    baseRotationDegrees: Int = 0
): KeyframedTransform = KeyframedTransform(
    positionX = clip.valueAt(KeyframeProperty.POSITION_X, timeMs, 0f).coerceIn(-1f, 1f),
    positionY = clip.valueAt(KeyframeProperty.POSITION_Y, timeMs, 0f).coerceIn(-1f, 1f),
    scaleX = clip.valueAt(KeyframeProperty.SCALE_X, timeMs, 1f).coerceIn(0.1f, 5f),
    scaleY = clip.valueAt(KeyframeProperty.SCALE_Y, timeMs, 1f).coerceIn(0.1f, 5f),
    rotationDegrees = clip.valueAt(
        KeyframeProperty.ROTATION, timeMs, baseRotationDegrees.toFloat()
    ).let { if (it.isFinite()) it.coerceIn(-360f, 360f) else 0f },
    cropZoom = clip.valueAt(KeyframeProperty.CROP_ZOOM, timeMs, baseCropZoom)
        .let { if (it.isFinite()) it.coerceIn(1f, 2.5f) else 1f }
)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun buildVideoEffects(
    cropZoom: Float,
    rotationDegrees: Int,
    outputPreset: String,
    inputShortSide: Int? = null
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

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun buildKeyframedVideoEffects(
    clip: TimelineClip,
    cropZoom: Float,
    rotationDegrees: Int,
    outputPreset: String,
    playbackSpeed: Float = 1f
): List<Effect> {
    val transformProperties = setOf(
        KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y,
        KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y,
        KeyframeProperty.ROTATION, KeyframeProperty.CROP_ZOOM
    )
    val hasTransformKeyframes = clip.keyframes.any { it.property in transformProperties }
    if (!hasTransformKeyframes) {
        return buildVideoEffects(cropZoom, rotationDegrees, outputPreset)
    }

    val effects = buildVideoEffects(1f, 0, outputPreset).toMutableList()
    val safeSpeed = playbackSpeed.coerceIn(0.25f, 3f)
    effects.add(0, MatrixTransformation { presentationTimeUs ->
        val localTimeMs = (presentationTimeUs.coerceAtLeast(0L) / 1_000f * safeSpeed).toLong()
        val value = evaluateKeyframedTransform(clip, localTimeMs, cropZoom, rotationDegrees)
        Matrix().apply {
            postScale(value.scaleX * value.cropZoom, value.scaleY * value.cropZoom)
            postRotate(value.rotationDegrees)
            postTranslate(value.positionX, value.positionY)
        }
    })
    return effects
}
