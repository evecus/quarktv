package com.quarktv.app.danmaku

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 云搜弹幕对话框：按片名搜索候选，选中后加载。
 * 直接输入弹幕地址也支持（在底部「手动加载」输入框——本简化版用候选点选即可）。
 */
@Composable
fun DanmuSearchDialog(
    name: String,
    episode: String,
    onDismiss: () -> Unit,
    onPicked: (DanmuSearchResult) -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }

    var state by remember { mutableStateOf<UiState>(UiState.Loading) }

    LaunchedEffect(name, episode) {
        state = UiState.Loading
        val result = withContext(Dispatchers.IO) { DanmakuApi.searchList(name, episode) }
        state = if (result.isError) UiState.Error(result.error)
        else if (result.results.isEmpty()) UiState.Empty
        else UiState.Ok(result.results)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(QuarkColors.Surface, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("搜弹幕 · $name${if (episode.isNotEmpty()) " 第${episode}集" else ""}",
                color = QuarkColors.TextPrimary, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            when (val s = state) {
                is UiState.Loading -> Text("搜索中…", color = QuarkColors.TextSecondary)
                is UiState.Error -> Text("搜索失败：${s.msg}", color = Color(0xFFFF8080))
                is UiState.Empty -> Text("没有找到匹配的弹幕", color = QuarkColors.TextSecondary)
                is UiState.Ok -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.results) { r ->
                        TvSurface(
                            onClick = { onPicked(r) },
                            shape = RoundedCornerShape(8.dp),
                            containerColor = QuarkColors.SurfaceHover,
                            focusedColor = QuarkColors.Primary,
                        ) {
                            Text(r.name.ifEmpty { "弹幕 ${r.url.hashCode()}" },
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvSurface(onClick = onDismiss, shape = RoundedCornerShape(8.dp),
                    containerColor = QuarkColors.SurfaceHover, focusedColor = QuarkColors.Primary) {
                    Text("返回", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White)
                }
            }
        }
    }
}

private sealed class UiState {
    object Loading : UiState()
    data class Ok(val results: List<DanmuSearchResult>) : UiState()
    object Empty : UiState()
    data class Error(val msg: String) : UiState()
}
