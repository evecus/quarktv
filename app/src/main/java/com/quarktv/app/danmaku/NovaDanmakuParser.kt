package com.quarktv.app.danmaku

import android.graphics.Color
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.Duration
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.SpecialDanmaku
import master.flame.danmaku.danmaku.model.android.DanmakuFactory
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.util.DanmakuUtils
import org.json.JSONArray

/**
 * 弹幕大师解析器：把 [DanmuItem] 列表转成库的 BaseDanmaku（含高级弹幕特殊处理）。
 * 精简自 ubox NovaDanmakuParser。
 */
class NovaDanmakuParser(private val items: List<DanmuItem>) : BaseDanmakuParser() {

    private var scaleX = 1f
    private var scaleY = 1f
    private var index = 0

    val itemCount: Int get() = items.size

    override fun parse(): Danmakus {
        val result = Danmakus(IDanmakus.ST_BY_TIME)
        for (data in items) {
            val danmaku = createDanmaku(data) ?: continue
            synchronized(result.obtainSynchronizer()) { result.addItem(danmaku) }
        }
        return result
    }

    override fun setDisplayer(display: IDisplayer): BaseDanmakuParser {
        super.setDisplayer(display)
        scaleX = mDispWidth.toFloat() / DanmakuFactory.BILI_PLAYER_WIDTH
        scaleY = mDispHeight.toFloat() / DanmakuFactory.BILI_PLAYER_HEIGHT
        return this
    }

    private fun createDanmaku(data: DanmuItem): BaseDanmaku? = try {
        val values = data.param.split(",")
        if (values.size < 4) return null
        val type = values[1].trim().toInt()
        val item = mContext.mDanmakuFactory.createDanmaku(type, mContext) ?: return null
        val time = (values[0].trim().toFloat() * 1000).toLong()
        val size = values[2].trim().toFloat() * maxOf(1.0f, mDispDensity - 0.6f)
        val color = if (DanmuSettings.randomColor) {
            DanmuSettings.pickRandomColor()
        } else {
            (0xFF000000L or values[3].trim().toLong()).toInt()
        }
        item.time = time
        item.setTimer(mTimer)
        item.textSize = size
        item.textColor = color
        item.textShadowColor = if (color <= Color.BLACK) Color.WHITE else Color.BLACK
        item.flags = mContext.mGlobalFlagValues
        item.index = index++
        DanmakuUtils.fillText(item, Danmu.decodeXmlString(data.text))
        if (item.type == BaseDanmaku.TYPE_SPECIAL &&
            data.text.startsWith("[") && data.text.endsWith("]") && !setSpecial(item)
        ) {
            return null
        }
        item
    } catch (_: Throwable) { null }

    /** 高级弹幕：text 为 JSON 数组。 */
    private fun setSpecial(item: BaseDanmaku): Boolean = try {
        val jsonArray = JSONArray(item.text)
        val textArr = Array(jsonArray.length()) { jsonArray.getString(it) }
        if (textArr.size < 5 || textArr[4].isEmpty()) return false
        DanmakuUtils.fillText(item, textArr[4])
        var beginX = textArr[0].toFloat()
        var beginY = textArr[1].toFloat()
        var endX = beginX
        var endY = beginY
        val alphaArr = textArr[2].split("-")
        val beginAlpha = (master.flame.danmaku.danmaku.model.AlphaValue.MAX * alphaArr[0].toFloat()).toInt()
        val endAlpha = if (alphaArr.size > 1) {
            (master.flame.danmaku.danmaku.model.AlphaValue.MAX * alphaArr[1].toFloat()).toInt()
        } else beginAlpha
        val alphaDuration = (textArr[3].toFloat() * 1000).toLong()
        var translationDuration = alphaDuration
        var translationStartDelay = 0L
        var rotateY = 0f
        var rotateZ = 0f
        if (textArr.size >= 7) { rotateZ = textArr[5].toFloat(); rotateY = textArr[6].toFloat() }
        if (textArr.size >= 11) {
            endX = textArr[7].toFloat(); endY = textArr[8].toFloat()
            if (textArr[9].isNotEmpty()) translationDuration = textArr[9].toLong()
            if (textArr[10].isNotEmpty()) translationStartDelay = textArr[10].toFloat().toLong()
        }
        if (isPercentageNumber(textArr[0])) beginX *= DanmakuFactory.BILI_PLAYER_WIDTH
        if (isPercentageNumber(textArr[1])) beginY *= DanmakuFactory.BILI_PLAYER_HEIGHT
        if (textArr.size >= 8 && isPercentageNumber(textArr[7])) endX *= DanmakuFactory.BILI_PLAYER_WIDTH
        if (textArr.size >= 9 && isPercentageNumber(textArr[8])) endY *= DanmakuFactory.BILI_PLAYER_HEIGHT
        item.duration = Duration(alphaDuration)
        item.rotationZ = rotateZ
        item.rotationY = rotateY
        mContext.mDanmakuFactory.fillTranslationData(
            item, beginX, beginY, endX, endY,
            translationDuration, translationStartDelay, scaleX, scaleY,
        )
        mContext.mDanmakuFactory.fillAlphaData(item, beginAlpha, endAlpha, alphaDuration)
        if (textArr.size >= 12 && "true".equals(textArr[11], ignoreCase = true)) {
            item.textShadowColor = Color.TRANSPARENT
        }
        if (textArr.size >= 14) (item as SpecialDanmaku).isQuadraticEaseOut = "0" == textArr[13]
        if (textArr.size >= 15 && textArr[14].isNotEmpty()) fillLinePath(item, textArr[14])
        true
    } catch (_: Throwable) { false }

    private fun fillLinePath(item: BaseDanmaku, motionPathString: String) {
        val motionPath = motionPathString.substring(1)
        if (motionPath.isEmpty()) return
        val points = motionPath.split("L").map { seg ->
            val p = seg.split(",")
            if (p.size < 2) return
            floatArrayOf(p[0].toFloat(), p[1].toFloat())
        }.toTypedArray()
        DanmakuFactory.fillLinePathData(item, points, scaleX, scaleY)
    }

    private fun isPercentageNumber(number: String) = number.contains(".")
}
