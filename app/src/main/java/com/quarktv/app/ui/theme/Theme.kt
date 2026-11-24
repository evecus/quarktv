package com.quarktv.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 官方夸克风格配色
object QuarkColors {
    val Background = Color(0xFF111318)
    val BackgroundDeep = Color(0xFF0A0C10)
    val Surface = Color(0xFF1C1F26)
    val SurfaceHover = Color(0xFF2B3545)
    val SurfaceCard = Color(0xFF171A21)
    val Primary = Color(0xFF3D7EFF)      // 夸克蓝
    val PrimarySoft = Color(0x293D7EFF)  // 10% 蓝，用于浅色底纹
    val FocusBorder = Color(0xFF5B9CFF)  // 焦点蓝框（比 Primary 更亮，TV 上更醒目）
    val TextPrimary = Color(0xFFEAECF2)
    val TextSecondary = Color(0xFF8A93A5)
    val Divider = Color(0xFF2A2F3A)
    val Danger = Color(0xFFFF8080)
}

/**
 * TV 放大字号：基于 Material3 默认 Typography 逐档放大。
 * 指纹/遥控器在 3~5 米观看，系统默认字号偏小，这里整体上调约 1.3~1.5 倍。
 * 全部页面统一通过 QuarkTheme 套用，一处生效。
 */
private val DefaultTypography = Typography()

val TvTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontSize = 76.sp),
    displayMedium = DefaultTypography.displayMedium.copy(fontSize = 60.sp),
    displaySmall  = DefaultTypography.displaySmall.copy(fontSize = 48.sp),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontSize = 44.sp),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontSize = 38.sp),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontSize = 32.sp),
    titleLarge = DefaultTypography.titleLarge.copy(fontSize = 30.sp),
    titleMedium = DefaultTypography.titleMedium.copy(fontSize = 24.sp),
    titleSmall = DefaultTypography.titleSmall.copy(fontSize = 20.sp),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontSize = 22.sp),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontSize = 19.sp),
    bodySmall = DefaultTypography.bodySmall.copy(fontSize = 16.sp),
    labelLarge = DefaultTypography.labelLarge.copy(fontSize = 20.sp),
    labelMedium = DefaultTypography.labelMedium.copy(fontSize = 17.sp),
    labelSmall = DefaultTypography.labelSmall.copy(fontSize = 15.sp),
)

/** 深色 colorScheme：让 M3 组件（输入框/指示器等）默认贴合暗色主题。 */
private val TvColorScheme = darkColorScheme(
    primary = QuarkColors.Primary,
    background = QuarkColors.Background,
    surface = QuarkColors.Surface,
    surfaceVariant = QuarkColors.SurfaceHover,
    onPrimary = Color.White,
    onBackground = QuarkColors.TextPrimary,
    onSurface = QuarkColors.TextPrimary,
    onSurfaceVariant = QuarkColors.TextSecondary,
    error = QuarkColors.Danger,
)

/** 统一主题：套用 TV 放大字号 + 暗色 colorScheme。 */
@Composable
fun QuarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = TvTypography,
        colorScheme = TvColorScheme,
        content = content,
    )
}

/** 全屏暗色渐变底容器（自上而下轻微明暗过渡，比纯黑更有层次）。 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(QuarkColors.Background, QuarkColors.BackgroundDeep),
                ),
            ),
        contentAlignment = contentAlignment,
    ) { content() }
}

/**
 * TV 可聚焦卡片：material3 Surface + 焦点高亮/平滑放大动画。
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
    focusBorderColor: Color = QuarkColors.FocusBorder,
    focusBorderWidth: Dp = 2.dp,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tvFocusScale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    onFocus(it.isFocused)
                }
            }
            .scale(scale),
        shape = shape,
        color = if (focused) focusedColor else containerColor,
        border = if (focused) BorderStroke(focusBorderWidth, focusBorderColor) else null,
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

/** 面板标题：左侧夸克蓝竖条 + 文字，用于弹窗/设置区块，统一视觉语言。 */
@Composable
fun PanelTitle(text: String, modifier: Modifier = Modifier, color: Color = QuarkColors.TextPrimary) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(QuarkColors.Primary),
        )
        Text(
            text,
            modifier = Modifier.padding(start = 10.dp),
            color = color,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
