package com.quarktv.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

// 官方夸克风格配色
object QuarkColors {
    val Background = Color(0xFF111318)
    val Surface = Color(0xFF1C1F26)
    val SurfaceHover = Color(0xFF2B3545)
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

/**
 * TV 可聚焦卡片：material3 Surface + 焦点高亮/放大。
 * 遥控器焦点由 Compose focus 系统自动管理（DPAD）。
 */
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    focusedColor: Color = QuarkColors.SurfaceHover,
    shape: Shape = RoundedCornerShape(10.dp),
    focusScale: Float = 1.05f,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    onFocus(it.isFocused)
                }
            }
            .scale(if (focused) focusScale else 1f),
        shape = shape,
        color = if (focused) focusedColor else containerColor,
    ) {
        Box(content = content)
    }
}

/** 静态圆角容器（卡片底色） */
@Composable
fun PlainBox(
    color: Color,
    shape: Shape = RoundedCornerShape(10.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.clip(shape).background(color),
        content = content,
    )
}
