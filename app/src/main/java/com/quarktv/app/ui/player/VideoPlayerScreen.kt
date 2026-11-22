package com.quarktv.app.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.quark.model.StreamVariant
import com.quarktv.app.ui.theme.QuarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频播放页（EXO 单内核）。
 * DPAD：OK=暂停/继续，左/右=±10s，上=清晰度菜单，返回=退出。
 * streaming 模式带 last_play_time 云续播。
 */
@Composable
fun VideoPlayerScreen(
    client: QuarkTvClient,
    file: QuarkFile,
    linkMethod: String,
    preferredResolution: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var variants by remember { mutableStateOf<List<StreamVariant>>(emptyList()) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var startAtMs by remember { mutableStateOf(0L) }
    var showMenu by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // 取直链
    LaunchedEffect(file.fid, linkMethod) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (linkMethod == "streaming") {
                    val link = client.streamingLink(file.fid)
                    variants = link.variants
                    startAtMs = link.lastPlayTime * 1000
                    val picked = if (preferredResolution.isEmpty()) link.variants.first()
                    else link.variants.firstOrNull { it.resolution == preferredResolution }
                        ?: link.variants.first()
                    picked
                } else {
                    val dl = client.downloadLink(file.fid)
                    StreamVariant("", dl.url, dl.size, 0, 0, 0)
                }
            }.onSuccess { v ->
                currentUrl = v.url
            }.onFailure { e ->
                error = e.message ?: "获取播放链接失败"
            }.also { withContext(Dispatchers.Main) { loading = false } }
        }
    }

    // 播放
    LaunchedEffect(currentUrl) {
        val url = currentUrl ?: return@LaunchedEffect
        val mime = if (url.substringBefore('?').endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (mime != null) setMimeType(mime) }
            .build()
        player.setMediaItem(item, startAtMs)
        player.prepare()
        player.playWhenReady = true
    }

    // 清晰度切换
    LaunchedEffect(showMenu, variants) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (e.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (showMenu) showMenu = false else onBack()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (showMenu) false
                        else {
                            paused = !paused
                            if (paused) player.pause() else player.play()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showMenu) { player.seekBack(10_000); true } else false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showMenu) { player.seekForward(10_000); true } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (variants.size > 1) { showMenu = true; true } else false
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = androidx.compose.ui.graphics.Color.White)
            }
        }
        if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("播放失败：$error", color = androidx.compose.ui.graphics.Color.White)
            }
        }

        // 清晰度菜单（按上键呼出）
        if (showMenu && variants.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(QuarkColors.Surface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("清晰度", color = QuarkColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    variants.forEach { v ->
                        Surface(
                            onClick = {
                                showMenu = false
                                val pos = player.currentPosition
                                val mime =
                                    if (v.url.substringBefore('?').endsWith(".m3u8"))
                                        MimeTypes.APPLICATION_M3U8 else null
                                val item = MediaItem.Builder()
                                    .setUri(v.url)
                                    .apply { if (mime != null) setMimeType(mime) }
                                    .build()
                                player.setMediaItem(item, pos)
                                player.prepare()
                                player.play()
                                paused = false
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            color = ClickableSurfaceDefaults.color(
                                container = QuarkColors.SurfaceHover,
                                focusedContainer = QuarkColors.Primary,
                            ),
                        ) {
                            Text(
                                v.resolution.uppercase(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
            }
        }

        // 底部操作提示
        if (!showMenu && !loading && error == null) {
            Text(
                "OK 暂停/继续 · ◀▶ ±10s · ▲ 清晰度 · 返回退出",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
