package com.quarktv.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// 官方夸克风格配色
object QuarkColors {
    val Background = Color(0xFF111318)
    val Surface = Color(0xFF1C1F26)
    val SurfaceHover = Color(0xFF262B35)
    val Primary = Color(0xFF3D7EFF)      // 夸克蓝
    val TextPrimary = Color(0xFFEAECF2)
    val TextSecondary = Color(0xFF8A93A5)
    val Divider = Color(0xFF2A2F3A)
}

/** 全屏黑底容器 */
@Composable
fun Screen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuarkColors.Background),
    ) { content() }
}
