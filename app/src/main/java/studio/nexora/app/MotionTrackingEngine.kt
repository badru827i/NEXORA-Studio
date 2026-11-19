package studio.nexora.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Normalized selection rectangle. Coordinates and size are in the 0..1 range. */
data class TrackingRegion(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.18f,
    val height: Float = 0.18f
)

/** A grayscale frame. The pixel array is row-major and contains unsigned 0..255 luma values. */
data class TrackingFrame(
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val luma: ByteArray
)

/** Automatically estimated target position at one sampled video time. */
data class MotionTrackPoint(
    val timestampMs: Long,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val confidence: Float
)

data class MotionTrackingResult(
    val points: List<MotionTrackPoint>,
    val framesAnalyzed: Int,
    val averageConfidence: Float,
    val lostAtMs: Long?,
    val message: String
)

/**
 * Lightweight CPU template tracker. It uses normalized cross-correlation over a selected
 * image patch and a coarse-to-fine local search. It has no native/OpenCV dependency.
 */
object MotionTrackingEngine {
    private data class Template(
        val width: Int,
        val height: Int,
        val pixels: FloatArray,
        val samples: IntArray,
        val sum: Double,
        val energy: Double
    )

    private data class Match(val x: Int, val y: Int, val confidence: Float)

    fun trackFrames(
        frames: List<TrackingFrame>,
        initialRegion: TrackingRegion,
        minimumConfidence: Float = 0.36f
    ): MotionTrackingResult {
        require(frames.isNotEmpty()) { "No video frames were decoded." }
        require(minimumConfidence in 0f..1f) { "Minimum confidence must be between 0 and 1." }
        val first = frames.first()
        validateFrame(first)
        require(initialRegion.width in 0.03f..0.8f && initialRegion.height in 0.03f..0.8f) {
            "Select a target area between 3% and 80% of the frame."
        }

        val roiWidth = (initialRegion.width * first.width).roundToInt().coerceIn(8, first.width / 2)
        val roiHeight = (initialRegion.height * first.height).roundToInt().coerceIn(8, first.height / 2)
        val initialLeft = (initialRegion.centerX.coerceIn(0f, 1f) * first.width - roiWidth / 2f)
            .roundToInt().coerceIn(0, first.width - roiWidth)
        val initialTop = (initialRegion.centerY.coerceIn(0f, 1f) * first.height - roiHeight / 2f)
            .roundToInt().coerceIn(0, first.height - roiHeight)
        var template = createTemplate(first, initialLeft, initialTop, roiWidth, roiHeight)
        require(template.energy > 1.0) {
            "The selected area has too little visual detail. Select a textured object or point."
        }

        val points = mutableListOf(
            MotionTrackPoint(
                first.timestampMs,
                (initialLeft + roiWidth / 2f) / first.width,
                (initialTop + roiHeight / 2f) / first.height,
                roiWidth.toFloat() / first.width,
                roiHeight.toFloat() / first.height,
                1f
            )
        )
        var previousLeft = initialLeft
        var previousTop = initialTop
        var lostAt: Long? = null

        for (frame in frames.drop(1)) {
            validateFrame(frame)
            require(frame.width == first.width && frame.height == first.height) {
                "All tracking frames must have the same dimensions."
            }
            val match = findBestMatch(frame, template, previousLeft, previousTop)
            if (match.confidence < minimumConfidence) {
                lostAt = frame.timestampMs
                break
            }

            previousLeft = match.x
            previousTop = match.y
            points += MotionTrackPoint(
                frame.timestampMs,
                (match.x + roiWidth / 2f) / frame.width,
                (match.y + roiHeight / 2f) / frame.height,
                roiWidth.toFloat() / frame.width,
                roiHeight.toFloat() / frame.height,
                match.confidence
            )
            // Adapt slowly to illumination and appearance changes without allowing a weak
            // match to immediately replace the original target appearance.
            if (match.confidence >= 0.62f) {
                template = adaptTemplate(template, frame, match.x, match.y, 0.08f)
            }
        }

        val average = points.map { it.confidence }.average().toFloat()
        val message = when {
            points.size <= 1 -> "Target could not be followed. Try a clearer, more detailed selection."
            lostAt != null -> "Tracking lost near ${lostAt} ms. ${points.size} keyframes recovered."
            else -> "Tracking complete. ${points.size} keyframes generated."
        }
        return MotionTrackingResult(points, frames.size, average, lostAt, message)
    }

    /**
     * Decode a selected video locally, sample up to 180 frames, and track the selected region.
     * Sampling is adaptive so long clips do not consume unbounded memory.
     */
    fun trackVideo(
        context: Context,
        uri: Uri,
        initialRegion: TrackingRegion,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): MotionTrackingResult {
        val retriever = MediaMetadataRetriever()
        val frames = ArrayList<TrackingFrame>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            require(durationMs > 0L) { "Unable to read the video duration." }
            val sampleCount = ceil(durationMs / 500.0).toInt().coerceIn(2, 180)
            for (index in 0 until sampleCount) {
                val timestampMs = if (sampleCount == 1) 0L
                    else durationMs * index / (sampleCount - 1)
                val bitmap = retriever.getFrameAtTime(
                    timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bitmap != null) {
                    frames += bitmap.toTrackingFrame(timestampMs)
                    bitmap.recycle()
                }
                onProgress(index + 1, sampleCount)
            }
        } finally {
            runCatching { retriever.release() }
        }
        require(frames.size >= 2) { "The video decoder returned fewer than two usable frames." }
        return trackFrames(frames, initialRegion)
    }

    private fun Bitmap.toTrackingFrame(timestampMs: Long): TrackingFrame {
        val scale = min(1f, 320f / max(width, height).toFloat())
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(this, (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1), true)
        } else this
        val argb = IntArray(scaled.width * scaled.height)
        scaled.getPixels(argb, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val gray = ByteArray(argb.size)
        for (i in argb.indices) {
            val pixel = argb[i]
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            gray[i] = ((red * 77 + green * 150 + blue * 29) shr 8).toByte()
        }
        val outputWidth = scaled.width
        val outputHeight = scaled.height
        if (scaled !== this) scaled.recycle()
        return TrackingFrame(timestampMs, outputWidth, outputHeight, gray)
    }

    private fun validateFrame(frame: TrackingFrame) {
        require(frame.width >= 16 && frame.height >= 16) { "Tracking frame is too small." }
        require(frame.luma.size == frame.width * frame.height) { "Tracking frame pixel data is invalid." }
    }

    private fun createTemplate(frame: TrackingFrame, left: Int, top: Int, width: Int, height: Int): Template {
        val values = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val value = (frame.luma[(top + y) * frame.width + left + x].toInt() and 0xff).toFloat()
            values[y * width + x] = value
        }
        val stride = max(1, min(width, height) / 20)
        val samples = buildList {
            for (y in 0 until height step stride) for (x in 0 until width step stride) add(y * width + x)
        }.toIntArray()
        val sampleSum = samples.sumOf { values[it].toDouble() }
        val mean = sampleSum / samples.size
        val energy = samples.sumOf { val d = values[it] - mean; d * d.toDouble() }
        return Template(width, height, values, samples, sampleSum, energy)
    }

    private fun findBestMatch(frame: TrackingFrame, template: Template, oldLeft: Int, oldTop: Int): Match {
        val radiusX = max(template.width, (frame.width * 0.18f).roundToInt())
        val radiusY = max(template.height, (frame.height * 0.18f).roundToInt())
        val coarseStep = max(2, min(template.width, template.height) / 8)
        val minX = (oldLeft - radiusX).coerceAtLeast(0)
        val maxX = (oldLeft + radiusX).coerceAtMost(frame.width - template.width)
        val minY = (oldTop - radiusY).coerceAtLeast(0)
        val maxY = (oldTop + radiusY).coerceAtMost(frame.height - template.height)
        var best = Match(oldLeft, oldTop, -1f)

        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val confidence = correlation(frame, template, x, y)
                if (confidence > best.confidence) best = Match(x, y, confidence)
                x += coarseStep
            }
            y += coarseStep
        }

        val refineMinX = (best.x - coarseStep).coerceAtLeast(0)
        val refineMaxX = (best.x + coarseStep).coerceAtMost(frame.width - template.width)
        val refineMinY = (best.y - coarseStep).coerceAtLeast(0)
        val refineMaxY = (best.y + coarseStep).coerceAtMost(frame.height - template.height)
        for (refineY in refineMinY..refineMaxY) {
            for (refineX in refineMinX..refineMaxX) {
                val confidence = correlation(frame, template, refineX, refineY)
                if (confidence > best.confidence) best = Match(refineX, refineY, confidence)
            }
        }
        return best
    }

    private fun correlation(frame: TrackingFrame, template: Template, left: Int, top: Int): Float {
        var sumB = 0.0
        var sumBB = 0.0
        var sumAB = 0.0
        val width = frame.width
        for (sample in template.samples) {
            val a = template.pixels[sample].toDouble()
            val b = (frame.luma[(top + sample / template.width) * width + left + sample % template.width]
                .toInt() and 0xff).toDouble()
            sumB += b
            sumBB += b * b
            sumAB += a * b
        }
        val count = template.samples.size.toDouble()
        val covariance = sumAB - template.sum * sumB / count
        val energyB = sumBB - sumB * sumB / count
        val denominator = sqrt(template.energy * energyB)
        return if (denominator <= 1e-6) -1f else (covariance / denominator).toFloat().coerceIn(-1f, 1f)
    }

    private fun adaptTemplate(
        template: Template, frame: TrackingFrame, left: Int, top: Int, blend: Float
    ): Template {
        val updated = template.pixels.copyOf()
        for (y in 0 until template.height) for (x in 0 until template.width) {
            val index = y * template.width + x
            val current = (frame.luma[(top + y) * frame.width + left + x].toInt() and 0xff).toFloat()
            updated[index] = updated[index] * (1f - blend) + current * blend
        }
        val sampleSum = template.samples.sumOf { updated[it].toDouble() }
        val mean = sampleSum / template.samples.size
        val energy = template.samples.sumOf { val d = updated[it] - mean; d * d.toDouble() }
        return template.copy(pixels = updated, sum = sampleSum, energy = energy)
    }
}
