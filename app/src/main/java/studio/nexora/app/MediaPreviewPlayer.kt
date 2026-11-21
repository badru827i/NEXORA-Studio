package studio.nexora.app

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun MediaPreviewPlayer(
    uri: Uri,
    speed: Float,
    modifier: Modifier = Modifier,
    videoEffects: List<Effect> = emptyList(),
    volume: Float = 1f,
    animationClip: TimelineClip? = null
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { setVideoEffects(videoEffects) } }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(uri, animationClip?.id, animationClip?.sourceInMs, animationClip?.durationMs) {
        player.stop()
        val builder = MediaItem.Builder().setUri(uri)
        if (animationClip != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(animationClip.sourceInMs)
                    .setEndPositionMs(animationClip.sourceInMs + animationClip.durationMs)
                    .build()
            )
        }
        player.setMediaItem(builder.build())
        player.prepare()
    }

    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
    }

    LaunchedEffect(volume, animationClip, speed) {
        while (true) {
            val animatedVolume = animationClip?.valueAt(
                KeyframeProperty.VOLUME,
                (player.currentPosition * speed.coerceIn(0.25f, 3f)).toLong(),
                1f
            ) ?: 1f
            player.volume = (volume * animatedVolume.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            delay(if (player.isPlaying) 33L else 120L)
        }
    }

    LaunchedEffect(videoEffects) {
        player.setVideoEffects(videoEffects)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.nexora_player_view, null, false) as PlayerView).apply {
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}
