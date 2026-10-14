package com.infraviored.glimpse.ui.viewer

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import com.infraviored.glimpse.R
import com.infraviored.glimpse.data.Item
import com.infraviored.glimpse.ui.photoBlur
import com.infraviored.glimpse.ui.LocalApp
import com.infraviored.glimpse.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * A video: its still until it plays; streamed from Nextcloud with the app's login, so it
 * starts without downloading the whole file.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPage(item: Item, active: Boolean, onTap: () -> Unit) {
    val api = LocalApp.current.api
    val context = LocalContext.current
    var started by remember(item.fileId, item.at) { mutableStateOf(false) }
    var resumePosition by remember(item.fileId) {
        mutableLongStateOf(item.at?.let { (it * 1000).toLong().coerceAtLeast(0L) } ?: 0L)
    }
    val player = remember(item.fileId, item.at, started) {
        if (!started) null else ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(api.http)))
            .setLoadControl(smallBuffer())
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(api.fileUrl(item)))
                if (resumePosition > 0L) seekTo(resumePosition)
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(active) {
        if (!active && started) {
            resumePosition = player?.currentPosition ?: resumePosition
            player?.pause()
            started = false
        }
    }

    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val presentation = player?.let { rememberPresentationState(it) }
        if (player == null || presentation?.coverSurface != false) {
            AsyncImage(
                model = api.previewUrl(item),
                placeholder = null,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().photoBlur(),
            )
        }
        if (player != null) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.resizeWithContentScale(ContentScale.Fit, presentation?.videoSizeDp).photoBlur(),
            )
            Controls(player, Modifier.align(Alignment.BottomCenter))
        } else {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Palette.Lens)
                    .clickable { started = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.video_play), tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun Controls(player: Player, modifier: Modifier) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
            if (seeking == null) position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 16.dp, bottom = 92.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable { if (player.isPlaying) player.pause() else player.play() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (playing) R.string.video_pause else R.string.video_play), tint = Color.White)
        }
        Text(clock(seeking?.let { (it * duration).toLong() } ?: position), color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = seeking ?: if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                seeking?.let { player.seekTo((it * duration).toLong()) }
                seeking = null
            },
            colors = SliderDefaults.colors(thumbColor = Palette.Apricot, activeTrackColor = Palette.Apricot, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(clock(duration), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
    }
}

private fun clock(ms: Long): String = com.infraviored.glimpse.ui.clock(ms / 1000)

/**
 * ExoPlayer buffers up to 128 MB of video by default, in the app's own heap (256 MB on most
 * phones): a few 4K videos from a phone camera, opened one after another, ran it out of memory.
 * 20 s ahead, at most 24 MB, is plenty for files streamed from one's own Nextcloud.
 */
@OptIn(UnstableApi::class)
private fun smallBuffer() = DefaultLoadControl.Builder()
    .setBufferDurationsMs(5_000, 20_000, 1_000, 2_000)
    .setTargetBufferBytes(24 * 1024 * 1024)
    .setPrioritizeTimeOverSizeThresholds(false)
    .build()
