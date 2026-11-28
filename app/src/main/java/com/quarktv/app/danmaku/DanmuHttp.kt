package com.quarktv.app.danmaku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** 弹幕网络小助手（精简自 ubox Http）：失败/非 2xx/空 body 统一返回 null。 */
internal object DanmuHttp {

    // 与 ubox Http.DEFAULT_UA 保持一致（ubox 已验证可用的请求特征）
    private const val UA = "okhttp/4.12.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        get(url)?.let { decodeBody(it) }
    }

    suspend fun getBytes(url: String): ByteArray? = withContext(Dispatchers.IO) { get(url) }

    suspend fun postForm(url: String, fields: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
                val req = Request.Builder().url(url).header("User-Agent", UA).post(body).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code !in 200..299) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    if (bytes.isEmpty()) null else decodeBody(bytes)
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun get(url: String): ByteArray? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA).get().build()
        client.newCall(req).execute().use { resp ->
            if (resp.code !in 200..299) return@use null
            val bytes = resp.body?.bytes() ?: return@use null
            if (bytes.isEmpty()) null else bytes
        }
    } catch (_: Exception) {
        null
    }

    /** gzip/zlib 魔数检测兜底解压，再按 UTF-8 解码。 */
    fun decodeBody(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return try { String(GZIPInputStream(bytes.inputStream()).use { it.readBytes() }, Charsets.UTF_8) }
            catch (_: Exception) { String(bytes, Charsets.UTF_8) }
        }
        if (bytes.size >= 2 && bytes[0] == 0x78.toByte()) {
            return try { String(InflaterInputStream(bytes.inputStream()).use { it.readBytes() }, Charsets.UTF_8) }
            catch (_: Exception) { String(bytes, Charsets.UTF_8) }
        }
        return String(bytes, Charsets.UTF_8)
    }
}
