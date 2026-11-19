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

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun MediaPreviewPlayer(uri: Uri, speed: Float, modifier: Modifier = Modifier, videoEffects: List<Effect> = emptyList()) {
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

    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
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
