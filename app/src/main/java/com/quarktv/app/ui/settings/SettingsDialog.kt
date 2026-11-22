package com.quarktv.app.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quarktv.app.data.SessionStore
import com.quarktv.app.danmaku.DanmuSettingsPanel
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface

/** 设置弹窗：直链方式、退出登录。返回键仅关闭弹窗，不退出 App。 */
@Composable
fun SettingsDialog(
    session: SessionStore,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    // 关键：注册 BackHandler 拦截系统返回键，只关闭弹窗
    BackHandler(enabled = true) { onDismiss() }

    var linkMethod by remember { mutableStateOf(session.linkMethod) }
    var resolution by remember { mutableStateOf(session.preferredResolution) }
    val resolutions = listOf(
        "" to "自动", "low" to "流畅", "normal" to "标清",
        "high" to "高清", "super" to "超清", "2k" to "2K", "4k" to "4K",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(max = 760.dp)
                .background(QuarkColors.Surface, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall,
                color = QuarkColors.TextPrimary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放方式", color = QuarkColors.TextSecondary,
                    modifier = Modifier.width(120.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = "转码流（推荐）",
                        selected = linkMethod == "streaming",
                        onClick = {
                            linkMethod = "streaming"
                            session.saveLinkMethod(linkMethod)
                        },
                    )
                    Chip(
                        text = "原画直链",
                        selected = linkMethod == "download",
                        onClick = {
                            linkMethod = "download"
                            session.saveLinkMethod(linkMethod)
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("清晰度", color = QuarkColors.TextSecondary,
                    modifier = Modifier.width(120.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    resolutions.forEach { (value, label) ->
                        Chip(
                            text = label,
                            selected = resolution == value,
                            onClick = {
                                resolution = value
                                session.savePreferredResolution(value)
                            },
                        )
                    }
                }
            }

            DanmuSettingsPanel()

            TvSurface(
                onClick = onLogout,
                shape = RoundedCornerShape(8.dp),
                containerColor = QuarkColors.SurfaceHover,
                focusedColor = QuarkColors.Primary,
                focusScale = 1f,
            ) {
                Text(
                    "退出登录",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = QuarkColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        containerColor = if (selected) QuarkColors.Primary else QuarkColors.SurfaceHover,
        focusedColor = QuarkColors.Primary,
        focusScale = 1f,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
