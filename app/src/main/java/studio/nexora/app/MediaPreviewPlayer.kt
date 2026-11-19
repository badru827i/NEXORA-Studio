package studio.nexora.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
fun MediaPreviewPlayer(uri: Uri, speed: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

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

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}
