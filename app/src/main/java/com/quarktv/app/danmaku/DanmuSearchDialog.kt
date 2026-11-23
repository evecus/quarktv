package com.quarktv.app.danmaku

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 弹幕云搜对话框（仿 ubox）：
 * 顶部是可编辑输入框（预填片名），用户可自行修改关键词，点「搜索」或回车触发；
 * 不会打开即自动按文件名搜索。选中候选后加载弹幕源。
 */
@Composable
fun DanmuSearchDialog(
    initialWord: String,
    episode: String,
    onDismiss: () -> Unit,
    onPicked: (DanmuSearchResult) -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }

    val scope = rememberCoroutineScope()
    val inputFocus = remember { FocusRequester() }
    var input by remember { mutableStateOf(initialWord) }
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }

    // 打开即聚焦输入框，遥控器可直接编辑/确认搜索
    LaunchedEffect(Unit) {
        repeat(6) {
            kotlinx.coroutines.delay(30)
            try { inputFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) {}
        }
    }

    fun doSearch() {
        val word = input.trim()
        if (word.isEmpty()) {
            state = UiState.Error("请输入片名")
            return
        }
        state = UiState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) { DanmakuApi.searchList(word, episode) }
            state = if (result.isError) UiState.Error(result.error)
            else if (result.results.isEmpty()) UiState.Empty
            else UiState.Ok(result.results)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(QuarkColors.Surface, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("弹幕云搜", color = QuarkColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium)

            // 可编辑输入框 + 搜索按钮（TV 焦点可操作）
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
                        Text("输入片名搜索弹幕", color = QuarkColors.TextSecondary,
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
    object Idle : UiState()
    object Loading : UiState()
    data class Ok(val results: List<DanmuSearchResult>) : UiState()
    object Empty : UiState()
    data class Error(val msg: String) : UiState()
}
