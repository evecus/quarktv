package com.quarktv.app.danmaku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quarktv.app.ui.theme.QuarkColors
import com.quarktv.app.ui.theme.TvSurface

private val SPEEDS = listOf(2.4, 1.8, 1.5, 1.0)
private val SPEED_NAMES = listOf("超慢", "慢", "适中", "快")
private val SIZES = listOf(0.8, 1.0, 1.2, 1.5, 2.0)

/** 弹幕设置区（嵌在设置弹窗里）。所有改动立即生效并持久化。 */
@Composable
fun DanmuSettingsPanel() {
    var open by remember { mutableStateOf(DanmuSettings.open) }
    var maxLine by remember { mutableStateOf(DanmuSettings.maxLine) }
    var speed by remember { mutableStateOf(DanmuSettings.speed) }
    var size by remember { mutableStateOf(DanmuSettings.sizeScale) }
    var alpha by remember { mutableStateOf(DanmuSettings.alpha) }
    var randomColor by remember { mutableStateOf(DanmuSettings.randomColor) }
    var customApi by remember { mutableStateOf(DanmuSettings.customApi) }

    fun apply(block: () -> Unit) {
        block()
        DanmakuManager.applySettings()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("弹幕设置", color = QuarkColors.Primary, style = MaterialTheme.typography.titleSmall)

        SettingRow("弹幕开关") {
            SegChip("开启", open) { apply { DanmuSettings.setOpen(true); open = true } }
            SegChip("关闭", !open) { apply { DanmuSettings.setOpen(false); open = false } }
        }
        SettingRow("显示行数") {
            listOf(1, 2, 3, 4, 6, 8).forEach { n ->
                SegChip("$n", maxLine == n) { apply { DanmuSettings.setMaxLine(n); maxLine = n } }
            }
        }
        SettingRow("滚动速度") {
            SPEEDS.zip(SPEED_NAMES).forEach { (v, label) ->
                SegChip(label, speed == v) { apply { DanmuSettings.setSpeed(v); speed = v } }
            }
        }
        SettingRow("字号") {
            SIZES.forEach { v ->
                SegChip("${v}x", size == v) { apply { DanmuSettings.setSizeScale(v); size = v } }
            }
        }
        SettingRow("透明度") {
            listOf(0.5, 0.7, 0.9, 1.0).forEach { v ->
                SegChip("$v", alpha == v) { apply { DanmuSettings.setAlpha(v); alpha = v } }
            }
        }
        SettingRow("随机颜色") {
            SegChip("开", randomColor) { apply { DanmuSettings.setRandomColor(true); randomColor = true } }
            SegChip("关", !randomColor) { apply { DanmuSettings.setRandomColor(false); randomColor = false } }
        }
        SettingRow("弹幕接口") {
            SegChip("内置", customApi.isEmpty()) {
                apply { DanmuSettings.setCustomApi(""); customApi = "" }
            }
            SegChip("自定义", customApi.isNotEmpty()) {
                // 自定义地址用简单输入框不便在 TV 展示，这里保留入口，地址可在 danmu_api 里预置
                customApi = DanmuSettings.customApi.ifEmpty { "https://" }
                DanmuSettings.setCustomApi(customApi)
                DanmakuManager.applySettings()
            }
        }
        if (customApi.isNotEmpty() && customApi != "https://") {
            Text(customApi, color = QuarkColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = QuarkColors.TextSecondary, modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f), content = content)
    }
}

@Composable
private fun SegChip(text: String, selected: Boolean, onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        containerColor = if (selected) QuarkColors.Primary else QuarkColors.SurfaceHover,
        focusedColor = QuarkColors.Primary,
        focusScale = 1f,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
