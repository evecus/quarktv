package com.quarktv.app.danmaku

import android.os.Looper
import android.view.View
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

/** 弹幕加载状态（供播放页提示）。 */
enum class DanmakuLoadStatus { IDLE, LOADING, OK, NONE, FAILED }

/** 单次加载结果。 */
enum class DanmakuLoadResult { OK, EMPTY, FAILED, SUPERSEDED, SKIPPED }

/**
 * 弹幕调度管理器（精简自 ubox DanmakuManager，逻辑保持一致）。
 * 视图与数据解耦：数据只在新加载/reset 时清空，视图每次 attach 用已加载数据重新 prepare，
 * 全屏切换/重组不丢弹幕；detach 带视图身份校验；库时间轴只在 seek 跳变/首次就绪时对齐。
 */
object DanmakuManager {

    private var view: DanmakuView? = null
    private val context: DanmakuContext by lazy { DanmakuContext.create() }

    @Volatile private var items: List<DanmuItem> = emptyList()
    private var prepared = false
    private var started = false
    private var lastPosMs = -1L
    @Volatile private var loadSeq = 0

    private val _status = MutableStateFlow(DanmakuLoadStatus.IDLE)
    val status: StateFlow<DanmakuLoadStatus> = _status.asStateFlow()

    val hasDanmaku: Boolean get() = items.isNotEmpty()
    val attached: Boolean get() = view != null

    private fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

    // ---------------- 视图生命周期 ----------------

    fun attach(v: DanmakuView) {
        view = v
        prepared = false
        started = false
        lastPosMs = -1
        applySettings()
        if (items.isNotEmpty() && DanmuSettings.open) {
            v.post { if (view === v) prepareView(v) }
        }
    }

    fun detach(v: DanmakuView) {
        if (view !== v) return
        view = null
        prepared = false
        started = false
        lastPosMs = -1
    }

    fun detachAll() {
        view = null
        prepared = false
        started = false
        lastPosMs = -1
    }

    private fun prepareView(v: DanmakuView): Boolean {
        val list = items
        if (list.isEmpty() || !DanmuSettings.open) return false
        return try {
            v.release()
            v.prepare(NovaDanmakuParser(list), context)
            v.visibility = View.VISIBLE
            prepared = true
            started = false
            lastPosMs = -1
            true
        } catch (_: Throwable) {
            prepared = false
            false
        }
    }

    // ---------------- 设置 ----------------

    fun applySettings() {
        if (!DanmuSettings.open) { releaseView(); return }
        val s = DanmuSettings
        val ctx = context
        ctx.setScrollSpeedFactor(s.speed.toFloat())
            .setDanmakuTransparency(s.alpha.toFloat())
            .setScaleTextSize(s.sizeScale.toFloat())
        ctx.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
            .setDanmakuMargin(s.lineSpacingPx)
        val overlapping = HashMap<Int, Boolean>(4).apply {
            put(BaseDanmaku.TYPE_SCROLL_RL, true)
            put(BaseDanmaku.TYPE_SCROLL_LR, true)
            put(BaseDanmaku.TYPE_FIX_TOP, true)
            put(BaseDanmaku.TYPE_FIX_BOTTOM, true)
        }
        ctx.preventOverlapping(overlapping)
        val maxLines = HashMap<Int, Int>(4).apply {
            put(BaseDanmaku.TYPE_FIX_TOP, s.maxLine)
            put(BaseDanmaku.TYPE_SCROLL_RL, s.maxLine)
            put(BaseDanmaku.TYPE_SCROLL_LR, s.maxLine)
            put(BaseDanmaku.TYPE_FIX_BOTTOM, s.maxLine)
        }
        ctx.setMaximumLines(maxLines)
    }

    suspend fun onSettingsChanged(reload: Boolean = false) {
        withContext(Dispatchers.Main) {
            applySettings()
            if (reload && DanmuSettings.open && items.isNotEmpty()) view?.let { prepareView(it) }
        }
    }

    // ---------------- 加载 ----------------

    /** 自动搜索并加载弹幕。 */
    suspend fun loadBySearch(name: String, episode: String): DanmakuLoadResult {
        clear()
        if (!DanmuSettings.open) return DanmakuLoadResult.SKIPPED
        if (name.isBlank()) return DanmakuLoadResult.SKIPPED
        val seq = ++loadSeq
        _status.value = DanmakuLoadStatus.LOADING
        val source = DanmakuApi.search(name, episode)
        if (seq != loadSeq) return DanmakuLoadResult.SUPERSEDED
        if (source.isNullOrEmpty()) {
            _status.value = DanmakuLoadStatus.NONE
            return DanmakuLoadResult.EMPTY
        }
        return load(source, seq, silent = false)
    }

    /** 直接加载弹幕源（云搜选中条目 / XML 地址）。 */
    suspend fun loadSource(source: String, silent: Boolean = false): DanmakuLoadResult {
        clear()
        if (!DanmuSettings.open) return DanmakuLoadResult.SKIPPED
        val seq = ++loadSeq
        _status.value = DanmakuLoadStatus.LOADING
        return load(source, seq, silent)
    }

    private suspend fun load(source: String, seq: Int, silent: Boolean): DanmakuLoadResult {
        val parser = try {
            DanmakuParser.create(source) { seq != loadSeq }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (seq != loadSeq) return DanmakuLoadResult.SUPERSEDED
        if (parser == null) {
            if (!silent) _status.value = DanmakuLoadStatus.FAILED
            return DanmakuLoadResult.FAILED
        }
        if (parser.itemCount <= 0) {
            if (!silent) _status.value = DanmakuLoadStatus.NONE
            return DanmakuLoadResult.EMPTY
        }
        var prepareOk = true
        withContext(Dispatchers.Main) {
            if (seq != loadSeq) return@withContext
            items = parser.items
            view?.let { prepareOk = prepareView(it) }
        }
        if (seq != loadSeq) return DanmakuLoadResult.SUPERSEDED
        if (!prepareOk) {
            if (!silent) _status.value = DanmakuLoadStatus.FAILED
            return DanmakuLoadResult.FAILED
        }
        _status.value = DanmakuLoadStatus.OK
        return DanmakuLoadResult.OK
    }

    fun clear() {
        items = emptyList()
        prepared = false
        started = false
        lastPosMs = -1
        releaseView()
        if (_status.value != DanmakuLoadStatus.IDLE) _status.value = DanmakuLoadStatus.IDLE
    }

    /** 换视频/退出：作废在途加载并清空。 */
    fun reset() {
        DanmakuApi.cancel()
        loadSeq++
        clear()
    }

    // ---------------- 播放同步 ----------------

    /** 位置跳变（seek）时对齐库时间轴；首次就绪时 start。 */
    fun syncPosition(positionMs: Long, playing: Boolean) {
        val v = view ?: return
        if (!prepared || !DanmuSettings.open) return
        val seeked = lastPosMs < 0 || abs(positionMs - lastPosMs) > 1000
        when {
            seeked -> {
                v.seekTo(positionMs)
                if (playing) { v.visibility = View.VISIBLE; v.start(positionMs); started = true }
                lastPosMs = positionMs
            }
            !started && playing -> {
                v.visibility = View.VISIBLE
                v.start(positionMs)
                started = true
                lastPosMs = positionMs
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        val v = view ?: return
        if (!prepared || !DanmuSettings.open) return
        if (playing) v.resume() else v.pause()
    }

    /** 缓冲中冻结弹幕动画，避免缓冲结束瞬间跳变。 */
    fun setBuffering(buffering: Boolean) {
        val v = view ?: return
        if (!prepared || !DanmuSettings.open) return
        if (buffering) v.pause() else v.resume()
    }

    private fun releaseView() {
        val v = view ?: return
        prepared = false
        started = false
        lastPosMs = -1
        val doRelease = {
            try { v.release() } catch (_: Throwable) {}
            v.visibility = View.GONE
        }
        if (isMainThread()) doRelease() else v.post { doRelease() }
    }
}
