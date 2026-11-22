package com.quarktv.app.danmaku

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** bilibili XML 弹幕条目（p 属性 + 文本）。 */
data class DanmuItem(val param: String, val text: String)

/** bilibili XML 弹幕解析：`<d p="time,type,size,color">text</d>` → [DanmuItem]。 */
object Danmu {

    private val D_TAG_PATTERN = Regex(
        "<d\\s+[^>]*\\bp\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</d>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun fromXml(xml: String): List<DanmuItem> {
        if (xml.isEmpty()) return emptyList()
        val out = mutableListOf<DanmuItem>()
        if (!parseByXmlPull(out, escapeIllegalEntities(xml))) {
            out.clear()
            parseByTag(out, xml)
        }
        return out
    }

    private fun parseByXmlPull(out: MutableList<DanmuItem>, xml: String): Boolean = try {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "d" == parser.name) {
                val param = parser.getAttributeValue(null, "p")
                val text = parser.nextText()
                if (!param.isNullOrEmpty() && !text.isNullOrEmpty()) out.add(DanmuItem(param, text))
            }
            eventType = parser.next()
        }
        out.isNotEmpty()
    } catch (_: Throwable) { false }

    private fun parseByTag(out: MutableList<DanmuItem>, xml: String) {
        for (m in D_TAG_PATTERN.findAll(xml)) {
            val param = decodeXmlString(m.groupValues[2])
            val text = m.groupValues[3]
            if (param.isNotEmpty() && text.isNotEmpty()) out.add(DanmuItem(param, text))
        }
    }

    /** 裸 `&` 且不是合法实体 → 补成 `&amp;`，否则 XmlPullParser 抛异常。 */
    private fun escapeIllegalEntities(xml: String): String {
        val sb = StringBuilder(xml.length)
        for (i in xml.indices) {
            val ch = xml[i]
            if (ch == '&' && !isLegalEntity(xml, i + 1)) sb.append("&amp;") else sb.append(ch)
        }
        return sb.toString()
    }

    private fun isLegalEntity(text: String, start: Int): Boolean {
        val end = text.indexOf(';', start)
        if (end < 0 || end - start > 10) return false
        val entity = text.substring(start, end)
        if (entity == "amp" || entity == "lt" || entity == "gt" || entity == "quot" || entity == "apos") return true
        if (entity.startsWith("#x") || entity.startsWith("#X")) return isHexNumber(entity, 2)
        return entity.startsWith("#") && isDecimalNumber(entity, 1)
    }

    private fun isDecimalNumber(text: String, start: Int): Boolean {
        if (text.length <= start) return false
        for (i in start until text.length) if (!text[i].isDigit()) return false
        return true
    }

    private fun isHexNumber(text: String, start: Int): Boolean {
        for (i in start until text.length) {
            val ch = text[i]
            if (!ch.isDigit() && (ch < 'a' || ch > 'f') && (ch < 'A' || ch > 'F')) return false
        }
        return true
    }

    fun decodeXmlString(text: String): String = if (text.isEmpty()) "" else text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&gt;", ">").replace("&lt;", "<")
}
