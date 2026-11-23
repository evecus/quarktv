package com.quarktv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.ui.browser.formatDuration
import com.quarktv.app.ui.theme.QuarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 音乐播放页（EXO 音频），布局对齐 NovaTV OpenList 音频播放页：
 * - 左半：专辑封面（有封面显示封面，无封面显示 🎵 + "暂无封面"）
 * - 右半：歌名 + 歌手/专辑 + 歌词区（暂无歌词占位）+ 进度条 + 时间
 * - 半透明黑底遮罩
 * - 遥控器：OK=暂停/继续，左/右=±10s，返回=退出
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
    var loading by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loading = state == androidx.media3.common.Player.STATE_BUFFERING ||
                        state == androidx.media3.common.Player.STATE_IDLE
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

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

    // 进度轮询（对齐 NovaTV 的 250ms）
    LaunchedEffect(file.fid) {
        while (true) {
            delay(250)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = if (player.duration > 0) player.duration else file.duration * 1000
        }
    }

    val title = file.filename.substringBeforeLast('.').let {
        if (it.isBlank()) file.filename else it
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
    ) {
        if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("播放失败：$error", color = QuarkColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
        // 半透明黑底遮罩（对齐 NovaTV #99000000 + app_bg）
        Box(Modifier.fillMaxSize().background(Color(0x99000000)))

        Row(
            modifier = Modifier.fillMaxSize().padding(40.dp),
        ) {
            // ── 左半：封面 ──────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (file.thumbnail.isNotEmpty()) {
                    AsyncImage(
                        model = file.thumbnail,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(320.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", style = MaterialTheme.typography.displayLarge,
                            color = Color.White.copy(alpha = 0.5f))
                        Text("暂无封面", color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            Spacer(Modifier.width(32.dp))

            // ── 右半：歌名 / 歌手 / 歌词 / 进度条 ──────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = QuarkColors.Primary,
                        modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                }

                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, textAlign = TextAlign.Center)

                if (file.size > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("夸克网盘", color = Color.White.copy(alpha = 0.66f),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }

                // 歌词区（暂无歌词占位）
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无歌词", color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.titleMedium)
                }

                // 进度条 + 时间
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(formatDuration(positionMs / 1000), color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                    Box(
                        modifier = Modifier.weight(1f).height(6.dp).padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(QuarkColors.Primary))
                    }
                    Text(formatDuration(durationMs / 1000), color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    if (paused) "已暂停 · OK 继续" else "播放中 · OK 暂停 · ◀▶ ±10s",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        } // end else
    }
}
