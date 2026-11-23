package studio.nexora.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.effect.HslAdjustment
import java.io.File
import java.io.FileInputStream

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class MediaExportManager(private val context: Context) {
    interface Callback {
        fun onComplete(outputFile: File, publicUri: Uri?)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun export(
        inputUri: Uri,
        trimStartPercent: Float,
        trimEndPercent: Float,
        playbackSpeed: Float,
        cropZoom: Float,
        rotationDegrees: Int,
        outputPreset: String,
        hue: Float,
        saturation: Float,
        lightness: Float,
        speedCurve: List<Float>,
        colorGrade: ColorGradeSettings = ColorGradeSettings(),
        callback: Callback
    ) {
        Thread {
            val durationMs = readDuration(inputUri)
            val inputShortSide = readShortSide(inputUri)
            if (durationMs <= 0L) {
                mainHandler.post { callback.onError("Could not read the video duration.") }
                return@Thread
            }

            val safeStart = trimStartPercent.coerceIn(0f, 99f)
            val safeEnd = trimEndPercent.coerceIn(safeStart + 1f, 100f)
            val startMs = (durationMs * safeStart / 100f).toLong().coerceIn(0L, durationMs - 1L)
            val endMs = (durationMs * safeEnd / 100f).toLong().coerceIn(startMs + 1L, durationMs)

            val outputDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "Movies")
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                mainHandler.post { callback.onError("Cannot create the export folder.") }
                return@Thread
            }
            val outputFile = File(outputDirectory, "NEXORA_" + System.currentTimeMillis() + ".mp4")

            mainHandler.post {
                try {
                    val clippedMedia = MediaItem.Builder()
                        .setUri(inputUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(startMs)
                                .setEndPositionMs(endMs)
                                .build()
                        )
                        .build()

                    val effectiveCurve = if (speedCurve.all { kotlin.math.abs(it - 1f) < 0.001f } && playbackSpeed != 1f) {
                        List(4) { playbackSpeed.coerceIn(0.25f, 3f) }
                    } else {
                        speedCurve.map { if (it.isFinite()) it.coerceIn(0.25f, 3f) else 1f }
                    }
                    val speedProvider = CurveSpeedProvider((endMs - startMs) * 1_000L, effectiveCurve)
                    val videoEffects = buildVideoEffects(cropZoom, rotationDegrees, outputPreset, inputShortSide) +
                        HslAdjustment.Builder()
                            .adjustHue(hue.coerceIn(-180f, 180f))
                            .adjustSaturation(saturation.coerceIn(-100f, 100f))
                            .adjustLightness(lightness.coerceIn(-100f, 100f))
                            .build() + buildColorGradeEffects(colorGrade)

                    val editedItem = EditedMediaItem.Builder(clippedMedia)
                        .setSpeed(speedProvider)
                        .setEffects(Effects(emptyList(), videoEffects))
                        .setFrameRate(30)
                        .build()

                    val listener = object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Thread {
                                val galleryUri = runCatching { copyToGallery(outputFile) }.getOrNull()
                                mainHandler.post { callback.onComplete(outputFile, galleryUri) }
                            }.start()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            outputFile.delete()
                            callback.onError(exportException.message ?: "Video export failed.")
                        }
                    }

                    Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(listener)
                        .build()
                        .start(editedItem, outputFile.absolutePath)
                } catch (error: Exception) {
                    outputFile.delete()
                    callback.onError(error.message ?: "Could not start video export.")
                }
            }
        }.start()
    }

    private fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readShortSide(uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            minOf(width, height).takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun copyToGallery(file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NEXORA Studio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the Movies library entry.")

        try {
            resolver.openOutputStream(destination, "w").use { output ->
                requireNotNull(output) { "Could not open the Movies output stream." }
                FileInputStream(file).use { input -> input.copyTo(output) }
            }
            val published = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(destination, published, null, null)
            return destination
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }
}


/**
 * Media3 SpeedProvider holds each speed until its next change. Sample the editable graph at
 * quarter-second intervals to approximate a smooth ramp while keeping the provider deterministic.
 */
private class CurveSpeedProvider(
    private val durationUs: Long,
    private val controlPoints: List<Float>
) : SpeedProvider {
    private val points = controlPoints.takeIf { it.size >= 2 } ?: listOf(1f, 1f)
    private val stepUs = 250_000L

    override fun getSpeed(timeUs: Long): Float {
        val progress = (timeUs.coerceAtLeast(0L).toDouble() / durationUs.coerceAtLeast(1L))
            .coerceIn(0.0, 1.0)
        val position = progress * (points.size - 1)
        val index = position.toInt().coerceIn(0, points.lastIndex - 1)
        val fraction = (position - index).toFloat().coerceIn(0f, 1f)
        val start = points[index].coerceIn(0.25f, 3f)
        val end = points[index + 1].coerceIn(0.25f, 3f)
        return (start + (end - start) * fraction).coerceIn(0.25f, 3f)
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        val next = (timeUs.coerceAtLeast(0L) / stepUs + 1L) * stepUs
        return if (next < durationUs) next else C.TIME_UNSET
    }
}
