package com.quarktv.app.danmaku

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quarktv.app.ui.theme.PanelTitle
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 弹幕云搜对话框（对齐 NovaTV SearchDanmuDialog 流程）：
 * - 打开即把当前视频文件名填入输入框，但不自动搜索；用户可修改关键词后点「搜索」/回车
 * - 搜索返回候选列表并自动聚焦首项
 * - 点选某条 → 弹窗内加载该条弹幕（loadSearchResult）→ 成功后回调弹幕文本并关闭
 * - 加载失败恢复列表并提示；无结果提示「未查询到匹配弹幕」
 */
@Composable
fun DanmuSearchDialog(
    initialWord: String,
    episode: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    var input by remember { mutableStateOf(initialWord) }
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }

    // 打开先聚焦输入框；搜索出结果后焦点落到首项（对齐 NovaTV gridView.requestFocus()）
    LaunchedEffect(Unit) {
        repeat(6) {
            delay(30)
            try { inputFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) {}
        }
    }
    LaunchedEffect(state) {
        val ok = state as? UiState.Ok
        if (ok != null && ok.results.isNotEmpty()) {
            repeat(6) {
                delay(30)
                try { firstResultFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) {}
            }
        }
    }

    fun doSearch() {
        val word = input.trim()
        if (word.isEmpty()) {
            Toast.makeText(context, "输入内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        state = UiState.Searching
        scope.launch {
            val result = withContext(Dispatchers.IO) { DanmakuApi.searchList(word, episode) }
            state = when {
                result.isError -> UiState.Error(result.error)
                result.results.isEmpty() -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "未查询到匹配弹幕", Toast.LENGTH_SHORT).show()
                    }
                    UiState.Empty
                }
                else -> UiState.Ok(result.results)
            }
        }
    }

    fun pick(result: DanmuSearchResult, results: List<DanmuSearchResult>) {
        state = UiState.LoadingDanmu
        scope.launch {
            val text = withContext(Dispatchers.IO) { DanmakuApi.loadSearchResult(result) }
            if (text.isEmpty()) {
                state = UiState.Ok(results, error = "加载失败，请重试")
            } else {
                onPicked(text)
                withContext(Dispatchers.Main) { onDismiss() }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(QuarkColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, QuarkColors.Divider, RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PanelTitle("弹幕云搜")

            // 可编辑输入框 + 搜索按钮（预填文件名，不自动搜索）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    placeholder = {
                        Text("请输入弹幕名称", color = QuarkColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = QuarkColors.Primary,
                        focusedBorderColor = QuarkColors.Primary,
                        unfocusedBorderColor = QuarkColors.TextSecondary,
                        focusedPlaceholderColor = QuarkColors.TextSecondary,
                        unfocusedPlaceholderColor = QuarkColors.TextSecondary,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(inputFocus),
                )
                TvSurface(
                    onClick = { doSearch() },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = QuarkColors.SurfaceHover,
                    focusedColor = QuarkColors.Primary,
                ) {
                    Text("搜索", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            when (val s = state) {
                is UiState.Idle -> Text("输入片名后点「搜索」或按确定", color = QuarkColors.TextSecondary)
                is UiState.Searching, is UiState.LoadingDanmu -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = QuarkColors.Primary,
                            modifier = Modifier.padding(end = 12.dp))
                        Text(if (s is UiState.LoadingDanmu) "加载弹幕中…" else "搜索中…",
                            color = QuarkColors.TextSecondary)
                    }
                }
                is UiState.Error -> Text("搜索失败：${s.msg}", color = Color(0xFFFF8080))
                is UiState.Empty -> Text("未查询到匹配弹幕", color = QuarkColors.TextSecondary)
                is UiState.Ok -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.error.isNotEmpty()) {
                            Text(s.error, color = Color(0xFFFF8080),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(s.results) { index, r ->
                                TvSurface(
                                    onClick = { pick(r, s.results) },
                                    modifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
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
    object Idle : UiState()
    object Searching : UiState()
    object LoadingDanmu : UiState()
    data class Ok(val results: List<DanmuSearchResult>, val error: String = "") : UiState()
    object Empty : UiState()
    data class Error(val msg: String) : UiState()
}
