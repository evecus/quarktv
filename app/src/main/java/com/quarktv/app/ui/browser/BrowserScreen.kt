package com.quarktv.app.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quarktv.app.data.SessionStore
import com.quarktv.app.quark.QuarkException
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.ui.player.AudioPlayerScreen
import com.quarktv.app.ui.player.VideoPlayerScreen
import com.quarktv.app.ui.settings.SettingsDialog
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 目录栈条目 */
data class DirEntry(val fid: String, val name: String)

/** 浏览器顶层：管理目录栈 + 播放页切换 */
@Composable
fun BrowserScreen(client: QuarkTvClient, session: SessionStore, onLogout: () -> Unit) {
    var stack by remember { mutableStateOf(listOf(DirEntry("0", "网盘"))) }
    var playingVideo by remember { mutableStateOf<QuarkFile?>(null) }
    var playingAudio by remember { mutableStateOf<QuarkFile?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    // 各目录的焦点位置记忆：fid -> index
    val focusByFid = remember { mutableStateMapOf<String, Int>() }

    when {
        playingVideo != null -> VideoPlayerScreen(
            client = client,
            file = playingVideo!!,
            linkMethod = session.linkMethod,
            preferredResolution = session.preferredResolution,
            onBack = { playingVideo = null },
        )
        playingAudio != null -> AudioPlayerScreen(
            client = client,
            file = playingAudio!!,
            onBack = { playingAudio = null },
        )
        else -> BrowserDir(
            client = client,
            entry = stack.last(),
            isRoot = stack.size == 1,
            breadcrumb = stack.joinToString(" / ") { it.name },
            initialFocusIndex = focusByFid[stack.last().fid] ?: 0,
            onTrackFocus = { fid, idx -> focusByFid[fid] = idx },
            onOpenDir = { entry -> stack = stack + DirEntry(entry.fid, entry.filename) },
            onBack = { stack = stack.dropLast(1) },
            onPlayVideo = { playingVideo = it },
            onPlayAudio = { playingAudio = it },
            onSettings = { showSettings = true },
        )
    }

    if (showSettings) {
        SettingsDialog(
            session = session,
            onDismiss = { showSettings = false },
            onLogout = {
                showSettings = false
                onLogout()
            },
        )
    }
}

/** 单个目录页 */
@Composable
private fun BrowserDir(
    client: QuarkTvClient,
    entry: DirEntry,
    isRoot: Boolean,
    breadcrumb: String,
    initialFocusIndex: Int,
    onTrackFocus: (fid: String, index: Int) -> Unit,
    onOpenDir: (QuarkFile) -> Unit,
    onBack: () -> Unit,
    onPlayVideo: (QuarkFile) -> Unit,
    onPlayAudio: (QuarkFile) -> Unit,
    onSettings: () -> Unit,
) {
    var files by remember(entry.fid) { mutableStateOf<List<QuarkFile>?>(null) }
    var error by remember(entry.fid) { mutableStateOf<String?>(null) }
    var focusIndex by remember(entry.fid) { mutableStateOf(initialFocusIndex) }
    var restorePending by remember(entry.fid) { mutableStateOf(initialFocusIndex > 0) }
    val focusRequesters = remember(entry.fid) { mutableMapOf<Int, FocusRequester>() }
    val listState = rememberLazyGridState()

    // 子目录按返回键回上级；根目录不拦截（交给系统默认，退出 App）
    BackHandler(enabled = !isRoot) { onBack() }

    LaunchedEffect(entry.fid) {
        withContext(Dispatchers.IO) {
            runCatching { client.list(entry.fid) }
                .onSuccess {
                    files = it.sortedWith(
                        compareByDescending<QuarkFile> { f -> f.isDir }
                            .thenByDescending { f -> f.updatedAt },
                    )
                }
                .onFailure { error = (it as? QuarkException)?.message ?: it.message ?: "加载失败" }
        }
    }

    // 从上级返回时恢复焦点
    LaunchedEffect(entry.fid, files) {
        if (files != null && restorePending) {
            restorePending = false
            runCatching {
                listState.scrollToItem(focusIndex)
                focusRequesters[focusIndex]?.requestFocus()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：品牌 + 面包屑 + 返回/设置
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 品牌标识：蓝点 + 应用名（根目录）/ 返回按钮（子目录）
            if (isRoot) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(QuarkColors.Primary),
                    )
                    Text(
                        "夸克网盘",
                        modifier = Modifier.padding(start = 12.dp),
                        color = QuarkColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                TvSurface(
                    onClick = onBack,
                    containerColor = QuarkColors.Surface,
                    focusedColor = QuarkColors.SurfaceHover,
                    shape = RoundedCornerShape(10.dp),
                    focusScale = 1f,
                ) {
                    Text(
                        "← 返回",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = QuarkColors.TextPrimary,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            // 面包屑（子目录时显示）
            if (!isRoot) {
                Text(
                    breadcrumb,
                    color = QuarkColors.TextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TvSurface(
                onClick = onSettings,
                containerColor = QuarkColors.Surface,
                focusedColor = QuarkColors.SurfaceHover,
                shape = RoundedCornerShape(10.dp),
                focusScale = 1f,
            ) {
                Text(
                    "设置",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = QuarkColors.TextPrimary,
                )
            }
        }

        when {
            files == null && error == null -> CenterHint("加载中…")
            error != null -> CenterHint("加载失败：$error")
            files!!.isEmpty() -> CenterHint("目录为空")
            else -> LazyVerticalGrid(
                state = listState,
                columns = GridCells.Fixed(5),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 32.dp, vertical = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(files!!.size) { index ->
                    val f = files!![index]
                    FileCard(
                        file = f,
                        onFocus = {
                            focusIndex = index
                            onTrackFocus(entry.fid, index)
                        },
                        onClick = {
                            focusIndex = index
                            onTrackFocus(entry.fid, index)
                            when {
                                f.isDir -> onOpenDir(f)
                                f.isVideo -> onPlayVideo(f)
                                f.isAudio -> onPlayAudio(f)
                            }
                        },
                        modifier = Modifier.focusRequester(
                            focusRequesters.getOrPut(index) { FocusRequester() },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = QuarkColors.TextSecondary)
    }
}

@Composable
private fun FileCard(
    file: QuarkFile,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier,
        onFocus = { if (it) onFocus() },
        containerColor = Color.Transparent,
        focusedColor = QuarkColors.Surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(QuarkColors.SurfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                if (file.thumbnail.isNotEmpty()) {
                    AsyncImage(
                        model = file.thumbnail,
                        contentDescription = file.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 缩略图底部轻微压暗，让角标更清晰
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color(0x66000000),
                                ),
                            ),
                    )
                } else {
                    Text(
                        if (file.isDir) "📁" else if (file.isAudio) "🎵" else "🎞",
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
                // 类型角标：目录加"文件夹"标签，其余显示时长
                if (file.isDir) {
                    Text(
                        "文件夹",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(QuarkColors.Primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (!file.isDir && file.duration > 0) {
                    Text(
                        formatDuration(file.duration),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color(0xD9000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                file.filename,
                modifier = Modifier.padding(top = 10.dp),
                color = QuarkColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!file.isDir && file.size > 0) {
                Text(
                    formatSize(file.size),
                    modifier = Modifier.padding(top = 2.dp),
                    color = QuarkColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (i == 0) "${bytes}B" else "%.1f%s".format(v, units[i])
}
