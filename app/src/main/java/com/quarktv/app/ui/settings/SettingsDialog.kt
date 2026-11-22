package com.quarktv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.quarktv.app.data.SessionStore
import com.quarktv.app.ui.theme.QuarkColors

/** 设置弹窗：直链方式、退出登录 */
@Composable
fun SettingsDialog(
    session: SessionStore,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    // 本地状态镜像 session，点击即时刷新 UI
    var linkMethod by remember { mutableStateOf(session.linkMethod) }
    var resolution by remember { mutableStateOf(session.preferredResolution) }
    val resolutions = listOf("" to "自动", "low" to "流畅", "normal" to "标清",
        "high" to "高清", "super" to "超清", "2k" to "2K", "4k" to "4K")

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(QuarkColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall,
                color = QuarkColors.TextPrimary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放方式", color = QuarkColors.TextSecondary,
                    modifier = Modifier.width(120.dp))
                Chip(
                    text = if (linkMethod == "download") "原画直链" else "转码流",
                    selected = true,
                    onClick = {
                        linkMethod = if (linkMethod == "download") "streaming" else "download"
                        session.saveLinkMethod(linkMethod)
                    },
                )
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

            Surface(
                onClick = onLogout,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                color = ClickableSurfaceDefaults.color(
                    container = QuarkColors.SurfaceHover,
                    focusedContainer = QuarkColors.Primary,
                ),
            ) {
                Text("退出登录", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = QuarkColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        color = ClickableSurfaceDefaults.color(
            container = if (selected) QuarkColors.Primary else QuarkColors.SurfaceHover,
            focusedContainer = if (selected) QuarkColors.Primary else QuarkColors.SurfaceHover,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color_White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White
