package com.quarktv.app.danmaku

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 弹幕设置（精简自 ubox DanmuSettings，持久化改为本 App 的 SharedPreferences）。
 * 渲染由 DanmakuFlameMaster 承担，各项设置映射到 DanmakuContext：
 *   open          → 开关（关闭即释放画面）
 *   maxLine       → setMaximumLines（1~30，默认 3）
 *   speed         → setScrollSpeedFactor（2.4 超慢 / 1.5 适中 / 1.0 快）
 *   alpha         → setDanmakuTransparency（0.1~1.0，默认 0.9）
 *   sizeScale     → setScaleTextSize（0.4~2.4，默认 1.2）
 *   lineSpacingPx → setDanmakuMargin（0~32px，默认 8）
 *   topMarginPx   → 弹幕 View 顶部偏移（0~300px，默认 40）
 *   randomColor   → 随机颜色
 *   customApi     → 自定义弹幕搜索接口（默认空=内置）
 */
object DanmuSettings {

    private const val PREF_NAME = "quarktv_danmu"
    private const val K_OPEN = "danmu_open"
    private const val K_MAX_LINE = "danmu_max_line"
    private const val K_SPEED = "danmu_speed"
    private const val K_ALPHA = "danmu_alpha"
    private const val K_SIZE_SCALE = "danmu_size_scale"
    private const val K_LINE_SPACING_PX = "danmu_line_spacing_px"
    private const val K_TOP_MARGIN_PX = "danmu_top_margin_px"
    private const val K_RANDOM_COLOR = "danmu_random_color"
    private const val K_CUSTOM_API = "danmu_api"

    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF70F3FF.toInt(), 0xFF44CEF6.toInt(), 0xFF3EEDE7.toInt(),
        0xFF00E079.toInt(), 0xFF2EDFA3.toInt(), 0xFFBCE672.toInt(), 0xFFFFF143.toInt(),
        0xFFFFA631.toInt(), 0xFFFF7500.toInt(), 0xFFFF4E20.toInt(), 0xFFFF2D51.toInt(),
        0xFFEF7A82.toInt(), 0xFFFF0097.toInt(), 0xFFB0A4E3.toInt(), 0xFF4B5CC4.toInt(),
    )

    private val random = Random.Default
    private lateinit var prefs: SharedPreferences

    @Volatile var open: Boolean = true; private set
    @Volatile var maxLine: Int = 3; private set
    @Volatile var speed: Double = 1.5; private set
    @Volatile var alpha: Double = 0.9; private set
    @Volatile var sizeScale: Double = 1.2; private set
    @Volatile var lineSpacingPx: Int = 8; private set
    @Volatile var topMarginPx: Int = 40; private set
    @Volatile var randomColor: Boolean = false; private set
    @Volatile var customApi: String = ""; private set

    /** 设置变更版本号：播放页据此重建弹幕区域。 */
    private val _revisionFlow = MutableStateFlow(0)
    val revisionFlow: StateFlow<Int> = _revisionFlow.asStateFlow()
    val revision: Int get() = _revisionFlow.value

    /** 必须在 Application/MainActivity 启动时调用一次。 */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        open = prefs.getBoolean(K_OPEN, true)
        maxLine = clampInt(prefs.getInt(K_MAX_LINE, 3), 1, 30)
        speed = prefs.getFloat(K_SPEED, 1.5f).toDouble()
        alpha = clampDouble(prefs.getFloat(K_ALPHA, 0.9f).toDouble(), 0.1, 1.0)
        sizeScale = clampDouble(prefs.getFloat(K_SIZE_SCALE, 1.2f).toDouble(), 0.4, 2.4)
        lineSpacingPx = clampInt(prefs.getInt(K_LINE_SPACING_PX, 8), 0, 32)
        topMarginPx = clampInt(prefs.getInt(K_TOP_MARGIN_PX, 40), 0, 300)
        randomColor = prefs.getBoolean(K_RANDOM_COLOR, false)
        customApi = prefs.getString(K_CUSTOM_API, "") ?: ""
    }

    private fun save() {
        if (!::prefs.isInitialized) return
        prefs.edit().apply {
            putBoolean(K_OPEN, open)
            putInt(K_MAX_LINE, maxLine)
            putFloat(K_SPEED, speed.toFloat())
            putFloat(K_ALPHA, alpha.toFloat())
            putFloat(K_SIZE_SCALE, sizeScale.toFloat())
            putInt(K_LINE_SPACING_PX, lineSpacingPx)
            putInt(K_TOP_MARGIN_PX, topMarginPx)
            putBoolean(K_RANDOM_COLOR, randomColor)
            putString(K_CUSTOM_API, customApi)
            apply()
        }
        _revisionFlow.value = _revisionFlow.value + 1
    }

    fun setOpen(v: Boolean) { open = v; save() }
    fun setMaxLine(v: Int) { maxLine = clampInt(v, 1, 30); save() }
    fun setSpeed(v: Double) { speed = round1(clampDouble(v, 0.5, 3.0)); save() }
    fun setAlpha(v: Double) { alpha = round1(clampDouble(v, 0.1, 1.0)); save() }
    fun setSizeScale(v: Double) { sizeScale = round1(clampDouble(v, 0.4, 2.4)); save() }
    fun setLineSpacingPx(v: Int) { lineSpacingPx = clampInt(v, 0, 32); save() }
    fun setTopMarginPx(v: Int) { topMarginPx = clampInt(v, 0, 300); save() }
    fun setRandomColor(v: Boolean) { randomColor = v; save() }
    fun setCustomApi(api: String) { customApi = api.trim(); save() }

    fun pickRandomColor(): Int = PALETTE[random.nextInt(PALETTE.size)]

    private fun clampInt(v: Int, min: Int, max: Int) = v.coerceIn(min, max)
    private fun clampDouble(v: Double, min: Double, max: Double) = v.coerceIn(min, max)
    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}
