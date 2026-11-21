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
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
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
        audioGain: Float = 1f,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        timeline: TimelineDocument? = null,
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

                    val speedProvider = object : SpeedProvider {
                        override fun getSpeed(presentationTimeUs: Long): Float =
                            playbackSpeed.coerceIn(0.25f, 3f)

                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                    }

                    val safeSpeed = playbackSpeed.coerceIn(0.25f, 3f)
                    val outputDurationUs = (((endMs - startMs).toDouble() / safeSpeed) * 1_000.0).toLong()
                    val safeFadeInUs = (fadeInMs.coerceAtLeast(0L) * 1_000L).coerceAtMost(outputDurationUs)
                    val safeFadeOutUs = (fadeOutMs.coerceAtLeast(0L) * 1_000L).coerceAtMost(outputDurationUs)
                    val gain = audioGain.coerceIn(0f, 1f)
                    val audioProcessors = if (gain < 0.999f || safeFadeInUs > 0L || safeFadeOutUs > 0L) {
                        val provider = DefaultGainProvider.Builder(gain).apply {
                            if (safeFadeInUs > 0L) {
                                addFadeAt(0L, safeFadeInUs, DefaultGainProvider.FADE_IN_LINEAR)
                            }
                            if (safeFadeOutUs > 0L) {
                                addFadeAt(
                                    (outputDurationUs - safeFadeOutUs).coerceAtLeast(0L),
                                    safeFadeOutUs,
                                    DefaultGainProvider.FADE_OUT_LINEAR
                                )
                            }
                        }.build()
                        listOf(GainProcessor(provider))
                    } else emptyList()

                    val editedItem = EditedMediaItem.Builder(clippedMedia)
                        .setSpeed(speedProvider)
                        .setEffects(Effects(audioProcessors, buildVideoEffects(cropZoom, rotationDegrees, outputPreset, inputShortSide)))
                        .setFrameRate(30)
                        .build()

                    val timelineComposition = timeline
                        ?.takeIf { document -> document.tracks.any { track ->
                            track.type == TimelineTrackType.VIDEO && track.clips.isNotEmpty()
                        } }
                        ?.let { document ->
                            buildTimelineComposition(
                                document = document,
                                playbackSpeed = safeSpeed,
                                cropZoom = cropZoom,
                                rotationDegrees = rotationDegrees,
                                outputPreset = outputPreset,
                                globalAudioGain = gain,
                                fadeInMs = fadeInMs,
                                fadeOutMs = fadeOutMs
                            )
                        }

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

                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(listener)
                        .build()
                    if (timelineComposition != null) {
                        transformer.start(timelineComposition, outputFile.absolutePath)
                    } else {
                        transformer.start(editedItem, outputFile.absolutePath)
                    }
                } catch (error: Exception) {
                    outputFile.delete()
                    callback.onError(error.message ?: "Could not start video export.")
                }
            }
        }.start()
    }

    
    private fun buildTimelineComposition(
        document: TimelineDocument,
        playbackSpeed: Float,
        cropZoom: Float,
        rotationDegrees: Int,
        outputPreset: String,
        globalAudioGain: Float,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Composition {
        document.exportValidationError()?.let { throw IllegalArgumentException(it) }
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val safeSpeed = playbackSpeed.coerceIn(0.25f, 3f)
        val textClips = document.tracks
            .filter { it.type == TimelineTrackType.TEXT && !it.muted }
            .flatMap { it.clips.filter { clip -> !clip.muted && clip.label.isNotBlank() } }

        document.tracks.forEach { track ->
            val clips = track.clips
                .filter { it.assetUri.isNotBlank() && !it.assetUri.startsWith("text://") }
                .sortedBy { it.startMs }
            if (clips.isEmpty()) return@forEach

            val trackTypes = when (track.type) {
                TimelineTrackType.VIDEO -> setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
                TimelineTrackType.AUDIO -> setOf(C.TRACK_TYPE_AUDIO)
                TimelineTrackType.TEXT -> return@forEach
            }
            val sequence = EditedMediaItemSequence.Builder(trackTypes)
            var cursorMs = 0L

            clips.forEach { clip ->
                require(clip.startMs >= cursorMs) {
                    "Track ${track.name} has overlapping clips. Move clips so they do not overlap before export."
                }
                if (clip.startMs > cursorMs) {
                    sequence.addGap((clip.startMs - cursorMs) * 1_000L)
                }

                val sourceUri = Uri.parse(clip.assetUri)
                val clipEndMs = clip.sourceInMs + clip.durationMs
                val mediaItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceInMs)
                            .setEndPositionMs(clipEndMs)
                            .build()
                    )
                    .build()

                val isVideo = track.type == TimelineTrackType.VIDEO
                val speedProvider = object : SpeedProvider {
                    override fun getSpeed(presentationTimeUs: Long): Float =
                        if (isVideo) safeSpeed else 1f

                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                }
                val clipGain = if (track.muted || clip.muted) 0f else {
                    (clip.volume.coerceIn(0f, 1f) * globalAudioGain).coerceIn(0f, 1f)
                }
                val audioProcessors = run {
                    val outputDurationUs = ((clip.durationMs.toDouble() / (if (isVideo) safeSpeed else 1f)) * 1_000.0).toLong()
                    val safeFadeInUs = fadeInMs.coerceAtLeast(0L).times(1_000L).coerceAtMost(outputDurationUs)
                    val safeFadeOutUs = fadeOutMs.coerceAtLeast(0L).times(1_000L).coerceAtMost(outputDurationUs)
                    val fadeProvider = DefaultGainProvider.Builder(clipGain).apply {
                        if (safeFadeInUs > 0L) addFadeAt(0L, safeFadeInUs, DefaultGainProvider.FADE_IN_LINEAR)
                        if (safeFadeOutUs > 0L) {
                            addFadeAt(
                                (outputDurationUs - safeFadeOutUs).coerceAtLeast(0L),
                                safeFadeOutUs,
                                DefaultGainProvider.FADE_OUT_LINEAR
                            )
                        }
                    }.build()
                    val hasVolumeKeyframes = clip.keyframes.any { it.property == KeyframeProperty.VOLUME }
                    val provider = object : GainProcessor.GainProvider {
                        override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
                            val fadeGain = fadeProvider.getGainFactorAtSamplePosition(samplePosition, sampleRate)
                            val outputTimeMs = samplePosition * 1_000L / sampleRate.coerceAtLeast(1)
                            val sourceTimeMs = (outputTimeMs * if (isVideo) safeSpeed else 1f).toLong()
                            val animatedVolume = clip.valueAt(KeyframeProperty.VOLUME, sourceTimeMs, 1f)
                                .coerceIn(0f, 1f)
                            return (fadeGain * animatedVolume).coerceIn(0f, 1f)
                        }

                        override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
                            if (hasVolumeKeyframes) C.TIME_UNSET
                            else fadeProvider.isUnityUntil(samplePosition, sampleRate)
                    }
                    listOf(ToInt16PcmAudioProcessor(), GainProcessor(provider))
                }
                val videoEffects = if (isVideo) {
                    buildKeyframedVideoEffects(clip, cropZoom, rotationDegrees, outputPreset, safeSpeed).toMutableList().apply {
                        buildTimelineTextOverlayEffect(
                            textClips = textClips,
                            videoClipStartMs = clip.startMs,
                            videoClipDurationMs = clip.durationMs,
                            playbackSpeed = safeSpeed
                        )?.let { add(it) }
                    }
                } else emptyList()
                val itemEffects = Effects(audioProcessors, videoEffects)
                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setSpeed(speedProvider)
                    .setEffects(itemEffects)
                    .setFrameRate(30)
                    .build()
                sequence.addItem(editedItem)
                cursorMs = clip.endMs
            }
            sequences += sequence.build()
        }

        require(sequences.any { it.trackTypes.contains(C.TRACK_TYPE_VIDEO) }) {
            "Add at least one video clip to a Video track before exporting."
        }
        return Composition.Builder(sequences).build()
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
