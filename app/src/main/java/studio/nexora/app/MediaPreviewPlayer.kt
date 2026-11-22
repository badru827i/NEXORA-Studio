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
fun MediaPreviewPlayer(uri: Uri, speed: Float, speedCurve: List<Float> = listOf(1f, 1f, 1f, 1f), modifier: Modifier = Modifier, videoEffects: List<Effect> = emptyList(), volume: Float = 1f) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { setVideoEffects(videoEffects) } }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(uri) {
        player.stop()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    LaunchedEffect(speed, speedCurve) {
        while (true) {
            val curve = if (speedCurve.all { kotlin.math.abs(it - 1f) < 0.001f } && speed != 1f) {
                List(4) { speed.coerceIn(0.25f, 3f) }
            } else {
                speedCurve.map { if (it.isFinite()) it.coerceIn(0.25f, 3f) else 1f }
            }
            val duration = player.duration
            val progress = if (duration > 0L) {
                (player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val position = progress * (curve.size - 1)
            val index = position.toInt().coerceIn(0, curve.lastIndex - 1)
            val fraction = (position - index).coerceIn(0f, 1f)
            val currentSpeed = curve[index] + (curve[index + 1] - curve[index]) * fraction
            player.setPlaybackSpeed(currentSpeed.coerceIn(0.25f, 3f))
            delay(120L)
        }
    }

    LaunchedEffect(volume) {
        player.volume = volume.coerceIn(0f, 1f)
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
