package com.quarktv.app.danmaku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.Inflater

/**
 * 弹幕源加载 + 解析入口：http(s) 地址拉取并解压，其余视为内联 XML。
 * 返回可直接交给 DanmakuView.prepare 的解析器；取不到内容返回 null。
 */
object DanmakuParser {

    suspend fun create(
        source: String,
        isCancelled: () -> Boolean = { false },
    ): NovaDanmakuParser? = withContext(Dispatchers.IO) {
        val text = source.trim()
        if (text.isEmpty()) return@withContext null
        val content = if (!text.startsWith("http")) text else resolveContent(text, isCancelled)
        if (content.isEmpty() || isCancelled()) return@withContext null
        NovaDanmakuParser(Danmu.fromXml(content))
    }

    private suspend fun resolveContent(source: String, isCancelled: () -> Boolean): String {
        if (isCancelled()) return ""
        val bytes = DanmuHttp.getBytes(source) ?: return ""
        if (bytes.isEmpty() || isCancelled()) return ""
        return decodeBody(bytes)
    }

    private fun decodeBody(bytes: ByteArray): String {
        // DanmuHttp.decodeBody 处理 gzip/zlib；这里补裸 deflate 兜底
        val decoded = DanmuHttp.decodeBody(bytes)
        if (decoded.isNotEmpty() && decoded.firstOrNull { !it.isWhitespace() } == '<') return decoded
        return try { inflateRaw(bytes) } catch (_: Exception) { decoded }
    }

    private fun inflateRaw(bytes: ByteArray): String {
        val inflater = Inflater(true)
        return try {
            java.util.zip.InflaterInputStream(bytes.inputStream(), inflater).use {
                String(it.readBytes(), Charsets.UTF_8)
            }
        } finally {
            inflater.end()
        }
    }
}
