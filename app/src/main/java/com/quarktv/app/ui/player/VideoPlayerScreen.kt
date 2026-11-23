package com.quarktv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.quarktv.app.danmaku.DanmakuManager
import com.quarktv.app.danmaku.DanmakuLoadResult
import com.quarktv.app.danmaku.DanmakuLoadStatus
import com.quarktv.app.danmaku.DanmuName
import com.quarktv.app.danmaku.DanmuSearchDialog
import com.quarktv.app.danmaku.DanmuSettings
import com.quarktv.app.danmaku.DanmuSettingsPanel
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.quark.model.StreamVariant
import com.quarktv.app.ui.browser.formatDuration
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import master.flame.danmaku.ui.widget.DanmakuView

/**
 * 视频播放页（EXO + DanmakuFlameMaster 弹幕），布局对齐 NovaTV OpenList 视频播放页：
 * - 顶部标题栏（文件名）
 * - 中间 loading / 暂停图标 / 快进快退预览浮层
 * - 底部控制条：功能按钮行（下一集 / 上一集 / 重播 / 刷新 / 清晰度 / 倍速 / 弹幕 / 搜弹幕）+ 进度条行
 * - 控制条 3 秒无操作自动隐藏；仅下键唤出并把焦点定位到首个按钮
 * - 控制条隐藏时：OK 暂停、左右快进快退预览（长按加速、抬起才 seek）、上键清晰度
 * - 控制条显示时：左右键交还焦点系统做按钮导航
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

    // ── 播放数据 ─────────────────────────────────────────────────────────────
    var currentFile by remember { mutableStateOf(file) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var sourceLabel by remember { mutableStateOf("") }
    var variants by remember { mutableStateOf<List<StreamVariant>>(emptyList()) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var startAtMs by remember { mutableStateOf(0L) }
    var refreshTick by remember { mutableStateOf(0) }

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // ── 控制条显隐 ───────────────────────────────────────────────────────────
    var showControls by remember { mutableStateOf(false) }
    var controlsTick by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDanmuSearch by remember { mutableStateOf(false) }
    var showDanmuSettings by remember { mutableStateOf(false) }

    // ── 快进快退预览浮层 ─────────────────────────────────────────────────────
    var showPreview by remember { mutableStateOf(false) }
    var previewDir by remember { mutableStateOf(1) }
    var previewTargetMs by remember { mutableStateOf(0L) }
    var previewTick by remember { mutableStateOf(0) }
    var seekActive by remember { mutableStateOf(false) }
    var seekOffset by remember { mutableStateOf(0L) }
    var lastSeekKeyTime by remember { mutableStateOf(0L) }

    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0f) }

    // ── 同目录上一集/下一集 ──────────────────────────────────────────────────
    var siblings by remember { mutableStateOf<List<QuarkFile>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(-1) }
    var siblingsLoaded by remember { mutableStateOf(false) }

    // ── 弹幕 ────────────────────────────────────────────────────────────────
    var danmuStatus by remember { mutableStateOf(DanmakuLoadStatus.IDLE) }
    // 弹窗内已完成 loadSearchResult，这里收到的是可直接加载的弹幕文本（内联 XML / 地址）
    var pendingDanmuText by remember { mutableStateOf<String?>(null) }
    val danmuRevision by DanmuSettings.revisionFlow.collectAsState()

    val rootFocus = remember { FocusRequester() }
    val firstBtnFocus = remember { FocusRequester() }

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

    // ── 取直链：转码优先，失败回退原画（切集 / 刷新时重新拉取）─────────────
    LaunchedEffect(currentFile.fid, linkMethod, refreshTick) {
        loading = true
        error = null
        variants = emptyList()
        sourceLabel = ""
        withContext(Dispatchers.IO) {
            runCatching {
                if (linkMethod == "streaming") {
                    val link = runCatching { client.streamingLink(currentFile.fid) }.getOrNull()
                    if (link != null) {
                        variants = link.variants
                        startAtMs = link.lastPlayTime * 1000
                        sourceLabel = "转码流"
                        if (preferredResolution.isEmpty()) link.variants.first()
                        else link.variants.firstOrNull { it.resolution == preferredResolution }
                            ?: link.variants.first()
                    } else {
                        val dl = client.downloadLink(currentFile.fid)
                        sourceLabel = "原画直链"
                        StreamVariant("", dl.url, dl.size, 0, 0, 0)
                    }
                } else {
                    val dl = client.downloadLink(currentFile.fid)
                    sourceLabel = "原画直链"
                    StreamVariant("", dl.url, dl.size, 0, 0, 0)
                }
            }.onSuccess { v -> currentUrl = v.url }
                .onFailure { error = it.message ?: "获取播放链接失败" }
                .also { withContext(Dispatchers.Main) { loading = false } }
        }
    }

    // ── 播放 ────────────────────────────────────────────────────────────────
    LaunchedEffect(currentUrl) {
        val url = currentUrl ?: return@LaunchedEffect
        val mime = if (url.substringBefore('?').endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
        val item = MediaItem.Builder().setUri(url)
            .apply { if (mime != null) setMimeType(mime) }.build()
        player.setMediaItem(item, startAtMs)
        player.prepare()
        player.playWhenReady = true
        paused = false
    }

    // ── 进度 + 弹幕时间轴同步 ────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = if (player.duration > 0) player.duration else 0L
            DanmakuManager.syncPosition(positionMs, player.isPlaying)
            delay(250)
        }
    }

    // ── 云搜选中并加载完成 → 载入弹幕文本 ───────────────────────────────────
    LaunchedEffect(pendingDanmuText) {
        val text = pendingDanmuText ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { DanmakuManager.loadSource(text) }
        danmuStatus = when (result) {
            DanmakuLoadResult.OK -> DanmakuLoadStatus.OK
            DanmakuLoadResult.EMPTY -> DanmakuLoadStatus.NONE
            else -> DanmakuLoadStatus.FAILED
        }
    }

    LaunchedEffect(danmuRevision) { DanmakuManager.onSettingsChanged(reload = true) }

    // ── 同目录兄弟视频（自然排序）──────────────────────────────────────────
    LaunchedEffect(file.fid) {
        withContext(Dispatchers.IO) {
            runCatching { client.list(file.parentFid) }
                .onSuccess { list ->
                    val vids = list.filter { it.isVideo }
                        .sortedWith { a, b -> naturalCompare(a.filename, b.filename) }
                    siblings = vids
                    currentIndex = vids.indexOfFirst { it.fid == file.fid }
                }
                .onFailure { siblings = emptyList(); currentIndex = -1 }
                .also { withContext(Dispatchers.Main) { siblingsLoaded = true } }
        }
    }

    // ── 控制条 3 秒自动隐藏（弹层打开时不计时）─────────────────────────────
    LaunchedEffect(showControls, controlsTick, showMenu, showDanmuSearch, showDanmuSettings) {
        if (showControls && !showMenu && !showDanmuSearch && !showDanmuSettings) {
            delay(3_000)
            showControls = false
        }
    }

    // ── 焦点管理：显示时给首按钮，隐藏时回根容器（保证下键可被捕获）────────
    LaunchedEffect(showControls) {
        repeat(8) {
            delay(30)
            try {
                if (showControls) firstBtnFocus.requestFocus() else rootFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {}
        }
    }

    // ── 快进快退预览浮层 1 秒后隐藏 ─────────────────────────────────────────
    LaunchedEffect(previewTick) {
        if (previewTick > 0 && !seekActive) {
            delay(1_000)
            showPreview = false
        }
    }

    fun togglePlay() {
        paused = !paused
        if (paused) player.pause() else player.play()
    }

    fun seekPreviewStart(dir: Int) {
        val dur = player.duration
        if (dur <= 0) return
        val now = System.currentTimeMillis()
        if (!seekActive) {
            seekActive = true
            seekOffset = 10_000L * dir
        } else if (now - lastSeekKeyTime <= 800) {
            seekOffset += 20_000L * dir
        } else {
            seekOffset = 10_000L * dir
        }
        lastSeekKeyTime = now
        previewTargetMs = (player.currentPosition + seekOffset).coerceIn(0, dur)
        previewDir = dir
        showPreview = true
        previewTick++
    }

    fun seekPreviewStop() {
        if (!seekActive) return
        player.seekTo(previewTargetMs)
        if (!player.isPlaying) player.play()
        seekActive = false
        seekOffset = 0L
        previewTick++
    }

    fun cycleSpeed() {
        val steps = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        var idx = 0
        for (i in steps.indices) if (kotlin.math.abs(steps[i] - speed) < 0.001f) { idx = i; break }
        speed = steps[(idx + 1) % steps.size]
        player.playbackParameters = PlaybackParameters(speed)
        controlsTick++
    }

    fun switchSibling(delta: Int) {
        if (!siblingsLoaded) return
        val t = currentIndex + delta
        if (t < 0 || t >= siblings.size) return
        currentIndex = t
        currentFile = siblings[t]
        DanmakuManager.reset()
        danmuStatus = DanmakuLoadStatus.IDLE
        pendingDanmuText = null
        refreshTick++
        controlsTick++
    }

    // ── 返回键层级：弹幕设置 → 搜弹幕 → 清晰度 → 控制条 → 退出 ──────────────
    BackHandler(enabled = true) {
        when {
            showDanmuSettings -> showDanmuSettings = false
            showDanmuSearch -> showDanmuSearch = false
            showMenu -> showMenu = false
            showControls -> showControls = false
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (showMenu || showDanmuSearch || showDanmuSettings) return@onPreviewKeyEvent false
                val down = e.type == KeyEventType.KeyDown
                if (showControls) {
                    // 控制条显示：所有按键交还焦点系统做按钮导航，任意键续期隐藏计时
                    if (down) controlsTick++
                    return@onPreviewKeyEvent false
                }
                // 控制条隐藏：OK 暂停、左右快进预览、上键清晰度、下键唤出控制条
                when (e.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (down) { togglePlay(); true } else false
                    }
                    Key.DirectionLeft -> {
                        if (down) seekPreviewStart(-1) else seekPreviewStop()
                        true
                    }
                    Key.DirectionRight -> {
                        if (down) seekPreviewStart(1) else seekPreviewStop()
                        true
                    }
                    Key.DirectionUp -> if (down && variants.size > 1) { showMenu = true; true } else false
                    Key.DirectionDown -> if (down) { showControls = true; controlsTick++; true } else false
                    else -> false
                }
            },
    ) {
        // 视频层
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )

        // 弹幕层
        val density = LocalDensity.current
        val topMarginDp = with(density) { DanmuSettings.topMarginPx.toDp() }
        AndroidView(
            factory = { ctx -> DanmakuView(ctx).also { DanmakuManager.attach(it) } },
            onRelease = { v -> DanmakuManager.detach(v); try { v.release() } catch (_: Exception) {} },
            modifier = Modifier.fillMaxSize().offset(y = topMarginDp),
        )

        // ── 中间 loading / 暂停图标 ─────────────────────────────────────────
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = QuarkColors.Primary)
            }
        } else if (buffering && error == null && !showPreview) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = QuarkColors.Primary)
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("播放失败：$error", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
        } else if (paused && !showControls && !showPreview) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.displayLarge)
            }
        }

        // ── 中间快进快退预览浮层 ────────────────────────────────────────────
        if (showPreview && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC111318))
                        .padding(20.dp),
                ) {
                    Text(if (previewDir > 0) "▶▶" else "◀◀", color = Color.White,
                        style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${formatDuration(previewTargetMs / 1000)} / ${formatDuration(durationMs / 1000)}",
                        color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // ── 顶部标题栏 ──────────────────────────────────────────────────────
        if (showControls && !showMenu && !showDanmuSearch && !showDanmuSettings) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Color(0x80000000)).padding(horizontal = 40.dp, vertical = 20.dp),
            ) {
                Text(currentFile.filename, color = Color.White,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
        }

        // ── 底部控制条 ──────────────────────────────────────────────────────
        if (showControls && !showMenu && !showDanmuSearch && !showDanmuSettings) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0x80000000)).padding(horizontal = 40.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 功能按钮行
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val hasNext = siblingsLoaded && currentIndex >= 0 && currentIndex < siblings.size - 1
                    val hasPre = siblingsLoaded && currentIndex > 0
                    CtrlButton("下一集", hasNext, Modifier.focusRequester(firstBtnFocus)) {
                        switchSibling(1)
                    }
                    CtrlButton("上一集", hasPre) { switchSibling(-1) }
                    CtrlButton("重播", true) {
                        player.seekTo(0); player.play(); paused = false; controlsTick++
                    }
                    CtrlButton("刷新", true) { refreshTick++; controlsTick++ }
                    if (variants.size > 1) {
                        CtrlButton("清晰度", true) { showMenu = true }
                    }
                    CtrlButton("x$speed", true) { cycleSpeed() }
                    CtrlButton("弹幕", true) { showDanmuSettings = true; controlsTick++ }
                    CtrlButton("搜弹幕", true) { showDanmuSearch = true; controlsTick++ }
                }

                // 进度条行
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(formatDuration(positionMs / 1000), color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                    Box(
                        modifier = Modifier.weight(1f).height(6.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                            .background(QuarkColors.Primary))
                    }
                    Text(formatDuration(durationMs / 1000), color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── 清晰度菜单 ──────────────────────────────────────────────────────
        if (showMenu && variants.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuarkColors.Surface.copy(alpha = 0.95f)).padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("清晰度", color = QuarkColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        variants.forEach { v ->
                            TvSurface(onClick = {
                                showMenu = false
                                val pos = player.currentPosition
                                val mime = if (v.url.substringBefore('?').endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
                                val item = MediaItem.Builder().setUri(v.url)
                                    .apply { if (mime != null) setMimeType(mime) }.build()
                                player.setMediaItem(item, pos)
                                player.prepare(); player.play(); paused = false
                                controlsTick++
                            }, shape = RoundedCornerShape(8.dp), focusedColor = QuarkColors.Primary) {
                                Text(v.resolution.uppercase(),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // ── 弹幕设置弹层 ────────────────────────────────────────────────────
        if (showDanmuSettings) {
            Box(Modifier.fillMaxSize().background(Color(0xDD000000)),
                contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.7f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuarkColors.Surface).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DanmuSettingsPanel()
                    TvSurface(onClick = { showDanmuSettings = false },
                        shape = RoundedCornerShape(8.dp), focusedColor = QuarkColors.Primary) {
                        Text("关闭", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = Color.White)
                    }
                }
            }
        }

        // ── 云搜弹幕 ────────────────────────────────────────────────────────
        if (showDanmuSearch) {
            DanmuSearchDialog(
                initialWord = DanmuName.title(currentFile.filename),
                episode = DanmuName.episode(currentFile.filename),
                onDismiss = { showDanmuSearch = false },
                // 弹窗内已完成选中条目的加载，这里收到可直载的弹幕文本
                onPicked = { danmuText -> pendingDanmuText = danmuText },
            )
        }
    }
}

@Composable
private fun CtrlButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(8.dp),
        containerColor = QuarkColors.Surface.copy(alpha = 0.7f),
        focusedColor = QuarkColors.Primary,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** 文件名自然排序：把连续数字整体按数值比较，"第2集" 排在 "第10集" 之前。 */
private fun naturalCompare(a0: String, b0: String): Int {
    val a = a0.ifEmpty { return -b0.length }
    val b = b0.ifEmpty { return a.length }
    var ia = 0; var ib = 0
    while (ia < a.length && ib < b.length) {
        val ca = a[ia]; val cb = b[ib]
        if (ca.isDigit() && cb.isDigit()) {
            val sa = ia; val sb = ib
            while (ia < a.length && a[ia].isDigit()) ia++
            while (ib < b.length && b[ib].isDigit()) ib++
            val na = a.substring(sa, ia).replaceFirst(Regex("^0+(?=.)"), "")
            val nb = b.substring(sb, ib).replaceFirst(Regex("^0+(?=.)"), "")
            if (na.length != nb.length) return na.length - nb.length
            val cmp = na.compareTo(nb)
            if (cmp != 0) return cmp
        } else {
            val cmp = ca.lowercaseChar() - cb.lowercaseChar()
            if (cmp != 0) return cmp
            ia++; ib++
        }
    }
    return (a.length - ia) - (b.length - ib)
}
