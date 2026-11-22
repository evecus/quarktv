package com.quarktv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.ui.browser.formatDuration
import com.quarktv.app.ui.browser.formatSize
import com.quarktv.app.ui.theme.QuarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 音乐播放页（EXO 音频）。
 * DPAD：OK=暂停/继续，左/右=±10s，返回=退出。
 */
@Composable
fun AudioPlayerScreen(
    client: QuarkTvClient,
    file: QuarkFile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // 返回键只退出音乐播放界面，不退出 App
    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(file.fid) {
        withContext(Dispatchers.IO) {
            runCatching { client.downloadLink(file.fid) }
                .onSuccess { link ->
                    val item = MediaItem.Builder().setUri(link.url).build()
                    withContext(Dispatchers.Main) {
                        player.setMediaItem(item)
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
                .onFailure { error = it.message ?: "获取播放链接失败" }
        }
    }

    // 进度轮询
    LaunchedEffect(file.fid) {
        while (true) {
            delay(500)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = if (player.duration > 0) player.duration else file.duration * 1000
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuarkColors.Background)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        paused = !paused
                        if (paused) player.pause() else player.play()
                        true
                    }
                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight -> {
                        player.seekTo(player.currentPosition + 10_000); true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (error == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎵", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(24.dp))
                Text(file.filename, color = QuarkColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                if (durationMs > 0) {
                    Text(
                        "${formatDuration(positionMs / 1000)} / ${formatDuration(durationMs / 1000)}",
                        color = QuarkColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (file.size > 0) {
                    Text(
                        formatSize(file.size),
                        color = QuarkColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    if (paused) "已暂停 · OK 继续" else "播放中 · OK 暂停 · ◀▶ ±10s",
                    color = QuarkColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            Text("播放失败：$error", color = QuarkColors.TextSecondary)
        }
    }
}
