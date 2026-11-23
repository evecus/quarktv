package com.quarktv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.quarktv.app.danmaku.DanmakuLoadResult
import com.quarktv.app.danmaku.DanmakuLoadStatus
import com.quarktv.app.danmaku.DanmakuManager
import com.quarktv.app.danmaku.DanmakuApi
import com.quarktv.app.danmaku.DanmuName
import com.quarktv.app.danmaku.DanmuSearchDialog
import com.quarktv.app.danmaku.DanmuSearchResult
import com.quarktv.app.danmaku.DanmuSettings
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.quark.model.StreamVariant
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import master.flame.danmaku.ui.widget.DanmakuView

/**
 * 视频播放页（EXO + DanmakuFlameMaster 弹幕）。
 * DPAD：OK=暂停/继续，左/右=±10s，上=清晰度菜单，返回=退出播放界面。
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
    var buffering by remember { mutableStateOf(false) }
    var sourceLabel by remember { mutableStateOf("") }
    var variants by remember { mutableStateOf<List<StreamVariant>>(emptyList()) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var startAtMs by remember { mutableStateOf(0L) }
    var showMenu by remember { mutableStateOf(false) }
    var showDanmuSearch by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }   // 控制条显隐（下键唤出）
    var controlsTick by remember { mutableStateOf(0) }       // 每次操作重置自动隐藏计时
    var paused by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val searchBtnFocus = remember { FocusRequester() }
    var danmuStatus by remember { mutableStateOf(DanmakuLoadStatus.IDLE) }
    var pickedDanmu by remember { mutableStateOf<DanmuSearchResult?>(null) }
    // 弹幕设置版本：变化时触发重组（topMargin 重算）与热更新
    val danmuRevision by DanmuSettings.revisionFlow.collectAsState()

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                DanmakuManager.setBuffering(state == Player.STATE_BUFFERING)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                DanmakuManager.setPlaying(isPlaying)
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "播放错误：${e.message ?: e.errorCodeName}"
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            DanmakuManager.detachAll()
            DanmakuManager.reset()
        }
    }

    BackHandler(enabled = true) {
        if (showDanmuSearch) showDanmuSearch = false
        else if (showMenu) showMenu = false
        else if (showControls) showControls = false
        else onBack()
    }

    // 控制条 3 秒无操作自动隐藏（弹层打开时不计时）
    LaunchedEffect(showControls, controlsTick, showMenu, showDanmuSearch) {
        if (showControls && !showMenu && !showDanmuSearch) {
            delay(3_000)
            showControls = false
        }
    }

    // 焦点管理：控制条显示 → 焦点给搜弹幕按钮；隐藏 → 焦点回根容器（保证下键可被根容器捕获）
    LaunchedEffect(showControls) {
        repeat(6) {
            delay(30)
            try {
                if (showControls) searchBtnFocus.requestFocus() else rootFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {}
        }
    }

    // 取直链：转码优先，失败回退原画
    LaunchedEffect(file.fid, linkMethod) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (linkMethod == "streaming") {
                    val link = runCatching { client.streamingLink(file.fid) }.getOrNull()
                    if (link != null) {
                        variants = link.variants
                        startAtMs = link.lastPlayTime * 1000
                        sourceLabel = "转码流"
                        if (preferredResolution.isEmpty()) link.variants.first()
                        else link.variants.firstOrNull { it.resolution == preferredResolution }
                            ?: link.variants.first()
                    } else {
                        val dl = client.downloadLink(file.fid)
                        sourceLabel = "原画直链"
                        StreamVariant("", dl.url, dl.size, 0, 0, 0)
                    }
                } else {
                    val dl = client.downloadLink(file.fid)
                    sourceLabel = "原画直链"
                    StreamVariant("", dl.url, dl.size, 0, 0, 0)
                }
            }.onSuccess { v -> currentUrl = v.url }
                .onFailure { error = it.message ?: "获取播放链接失败" }
                .also { withContext(Dispatchers.Main) { loading = false } }
        }
    }

    // 播放
    LaunchedEffect(currentUrl) {
        val url = currentUrl ?: return@LaunchedEffect
        val mime = if (url.substringBefore('?').endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
        val item = MediaItem.Builder().setUri(url)
            .apply { if (mime != null) setMimeType(mime) }.build()
        player.setMediaItem(item, startAtMs)
        player.prepare()
        player.playWhenReady = true
    }

    // 弹幕进度同步（轮询）
    LaunchedEffect(Unit) {
        while (true) {
            DanmakuManager.syncPosition(player.currentPosition, player.isPlaying)
            delay(300)
        }
    }

    // 云搜选中条目 → 加载弹幕源
    LaunchedEffect(pickedDanmu) {
        val r = pickedDanmu ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            val src = DanmakuApi.loadSearchResult(r)
            if (src.isNotEmpty()) DanmakuManager.loadSource(src) else DanmakuLoadResult.FAILED
        }
        danmuStatus = when (result) {
            DanmakuLoadResult.OK -> DanmakuLoadStatus.OK
            DanmakuLoadResult.EMPTY -> DanmakuLoadStatus.NONE
            else -> DanmakuLoadStatus.FAILED
        }
    }

    // 外部（设置弹窗）改动弹幕设置后热更新 + 重备
    LaunchedEffect(danmuRevision) {
        DanmakuManager.onSettingsChanged(reload = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // 弹层（搜索/清晰度）打开时不拦截，交给弹层自己处理
                if (showMenu || showDanmuSearch) return@onPreviewKeyEvent false
                when (e.key) {
                    // 下键唤出控制条；已在显示时续期
                    Key.DirectionDown -> {
                        showControls = true
                        controlsTick++
                        true
                    }
                    else -> if (showControls) {
                        // 控制条可见：只续期隐藏计时，不拦截，让方向键/OK 交给焦点按钮
                        controlsTick++
                        false
                    } else {
                        // 控制条隐藏：OK 暂停、左右 ±10s、上键清晰度
                        when (e.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                paused = !paused
                                if (paused) player.pause() else player.play()
                                true
                            }
                            Key.DirectionLeft -> {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                true
                            }
                            Key.DirectionRight -> {
                                player.seekTo(player.currentPosition + 10_000)
                                true
                            }
                            Key.DirectionUp -> if (variants.size > 1) {
                                showMenu = true
                                true
                            } else false
                            else -> false
                        }
                    }
                }
            },
    ) {
        // 视频层
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )

        // 弹幕层（独立于视频层，offset 下移顶部边距）
        val density = LocalDensity.current
        val topMarginDp = with(density) { DanmuSettings.topMarginPx.toDp() }
        AndroidView(
            factory = { ctx -> DanmakuView(ctx).also { DanmakuManager.attach(it) } },
            onRelease = { v -> DanmakuManager.detach(v); try { v.release() } catch (_: Exception) {} },
            modifier = Modifier.fillMaxSize().offset(y = topMarginDp),
        )

        if (loading) OverlayText("加载中…")
        if (buffering && !loading && error == null) OverlayText("缓冲中…")
        if (error != null) OverlayText("播放失败：$error")

        // 右上角控制条（下键唤出，3 秒无操作自动隐藏）
        if (showControls) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvSurface(
                    onClick = { showDanmuSearch = true },
                    modifier = Modifier.focusRequester(searchBtnFocus),
                    shape = RoundedCornerShape(8.dp),
                    containerColor = QuarkColors.Surface.copy(alpha = 0.7f),
                    focusedColor = QuarkColors.Primary,
                ) {
                    Text("搜弹幕", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                if (variants.size > 1) {
                    TvSurface(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        containerColor = QuarkColors.Surface.copy(alpha = 0.7f),
                        focusedColor = QuarkColors.Primary,
                    ) {
                        Text("清晰度", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 清晰度菜单
        if (showMenu && variants.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .background(QuarkColors.Surface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("清晰度", color = QuarkColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    variants.forEach { v ->
                        TvSurface(onClick = {
                            showMenu = false
                            val pos = player.currentPosition
                            val mime = if (v.url.substringBefore('?').endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
                            val item = MediaItem.Builder().setUri(v.url)
                                .apply { if (mime != null) setMimeType(mime) }.build()
                            player.setMediaItem(item, pos)
                            player.prepare(); player.play(); paused = false
                        }, shape = RoundedCornerShape(8.dp), focusedColor = QuarkColors.Primary) {
                            Text(v.resolution.uppercase(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Color.White)
                        }
                    }
                }
            }
        }

        // 底部状态（随控制条一起显隐）
        if (showControls && !showMenu && !showDanmuSearch && !loading && error == null) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val dm = when (danmuStatus) {
                    DanmakuLoadStatus.OK -> if (DanmuSettings.open) "开" else "关"
                    DanmakuLoadStatus.LOADING -> "搜索中"
                    DanmakuLoadStatus.NONE -> "无"
                    DanmakuLoadStatus.FAILED -> "失败"
                    else -> "关"
                }
                Text("片源：$sourceLabel　弹幕：$dm", color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall)
                Text("▼ 控制条 · OK 暂停/继续 · ◀▶ ±10s · ▲ 清晰度 · 返回退出播放",
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }

        if (showDanmuSearch) {
            DanmuSearchDialog(
                initialWord = DanmuName.title(file.filename),
                episode = DanmuName.episode(file.filename),
                onDismiss = { showDanmuSearch = false },
                onPicked = { r -> pickedDanmu = r; showDanmuSearch = false },
            )
        }
    }
}

@Composable
private fun OverlayText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White)
    }
}
