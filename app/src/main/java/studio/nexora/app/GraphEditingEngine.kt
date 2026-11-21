package studio.nexora.app

import kotlin.math.pow

/** Normalized editable animation curve. X is time (0..1); Y is the property value. */
data class GraphKeyframe(
    val time: Float,
    val value: Float,
    val interpolation: GraphInterpolation = GraphInterpolation.EASE_IN_OUT
)

enum class GraphInterpolation { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, HOLD }

/**
 * Shared graph evaluator for grading, blend opacity, speed ramps, audio automation
 * and transform keyframes. Input is sanitized so malformed project data cannot crash playback.
 */
data class GraphCurve(val keyframes: List<GraphKeyframe>) {
    fun normalized(): GraphCurve {
        val sorted = keyframes
            .filter { it.time.isFinite() && it.value.isFinite() }
            .map { it.copy(time = it.time.coerceIn(0f, 1f)) }
            .sortedBy { it.time }
            .fold(mutableListOf<GraphKeyframe>()) { acc, point ->
                if (acc.isNotEmpty() && kotlin.math.abs(acc.last().time - point.time) < 0.0001f) {
                    acc[acc.lastIndex] = point
                } else acc += point
                acc
            }
        return GraphCurve(sorted)
    }

    fun valueAt(time: Float, fallback: Float = 0f): Float {
        val points = normalized().keyframes
        if (points.isEmpty()) return fallback
        val x = if (time.isFinite()) time.coerceIn(0f, 1f) else 0f
        if (x <= points.first().time) return points.first().value
        if (x >= points.last().time) return points.last().value
        val rightIndex = points.indexOfFirst { it.time >= x }.coerceAtLeast(1)
        val left = points[rightIndex - 1]
        val right = points[rightIndex]
        val span = (right.time - left.time).coerceAtLeast(0.0001f)
        val raw = ((x - left.time) / span).coerceIn(0f, 1f)
        val t = when (left.interpolation) {
            GraphInterpolation.LINEAR -> raw
            GraphInterpolation.EASE_IN -> raw * raw
            GraphInterpolation.EASE_OUT -> 1f - (1f - raw) * (1f - raw)
            GraphInterpolation.EASE_IN_OUT -> if (raw < 0.5f) 2f * raw * raw else 1f - (-2f * raw + 2f).pow(2) / 2f
            GraphInterpolation.HOLD -> 0f
        }
        return left.value + (right.value - left.value) * t
    }

    companion object {
        fun constant(value: Float) = GraphCurve(listOf(
            GraphKeyframe(0f, value, GraphInterpolation.LINEAR),
            GraphKeyframe(1f, value, GraphInterpolation.LINEAR)
        ))
    }
}

data class ColorGrade(
    val exposureEv: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val hue: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f
) {
    fun safe() = copy(
        exposureEv = exposureEv.finiteClamp(-5f, 5f),
        contrast = contrast.finiteClamp(0f, 3f),
        saturation = saturation.finiteClamp(0f, 3f),
        temperature = temperature.finiteClamp(-1f, 1f),
        tint = tint.finiteClamp(-1f, 1f),
        hue = hue.finiteClamp(-180f, 180f),
        highlights = highlights.finiteClamp(-1f, 1f),
        shadows = shadows.finiteClamp(-1f, 1f)
    )
}

enum class BlendMode(val label: String) {
    NORMAL("Normal"), MULTIPLY("Multiply"), SCREEN("Screen"), OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"), HARD_LIGHT("Hard Light"), ADD("Add"),
    DIFFERENCE("Difference"), DARKEN("Darken"), LIGHTEN("Lighten")
}

/** Blend one normalized color channel, then composite using the layer opacity. */
fun blendChannel(base: Float, layer: Float, opacity: Float, mode: BlendMode): Float {
    val b = base.finiteClamp(0f, 1f)
    val s = layer.finiteClamp(0f, 1f)
    val a = opacity.finiteClamp(0f, 1f)
    val mixed = when (mode) {
        BlendMode.NORMAL -> s
        BlendMode.MULTIPLY -> b * s
        BlendMode.SCREEN -> 1f - (1f - b) * (1f - s)
        BlendMode.OVERLAY -> if (b <= 0.5f) 2f * b * s else 1f - 2f * (1f - b) * (1f - s)
        BlendMode.HARD_LIGHT -> if (s <= 0.5f) 2f * b * s else 1f - 2f * (1f - b) * (1f - s)
        BlendMode.SOFT_LIGHT -> (1f - 2f * s) * b * b + 2f * s * b
        BlendMode.ADD -> (b + s).coerceAtMost(1f)
        BlendMode.DIFFERENCE -> kotlin.math.abs(b - s)
        BlendMode.DARKEN -> minOf(b, s)
        BlendMode.LIGHTEN -> maxOf(b, s)
    }
    return (b + (mixed - b) * a).coerceIn(0f, 1f)
}

/** Speed graph values are playback multipliers, clamped to a safe editing range. */
data class SpeedRamp(val curve: GraphCurve) {
    fun speedAt(normalizedTime: Float): Float = curve.valueAt(normalizedTime, 1f).finiteClamp(0.1f, 8f)
}

private fun Float.finiteClamp(min: Float, max: Float): Float =
    if (isFinite()) coerceIn(min, max) else min
