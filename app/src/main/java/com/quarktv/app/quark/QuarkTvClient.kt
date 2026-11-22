package com.quarktv.app.quark

import com.quarktv.app.quark.model.DownloadLink
import com.quarktv.app.quark.model.QuarkFile
import com.quarktv.app.quark.model.Resp
import com.quarktv.app.quark.model.StreamLink
import com.quarktv.app.quark.model.StreamVariant
import com.quarktv.app.quark.model.TokenPair
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 夸克/UC 网盘 TV 版协议客户端。
 * 逐行对齐 OpenList drivers/quark_uc_tv（util.go / driver.go / meta.go）。
 */
class QuarkTvClient(
    private val conf: Conf = Conf.quark(),
    private val session: SessionProvider,
) {

    data class Conf(
        val api: String,
        val clientID: String,
        val signKey: String,
        val appVer: String,
        val channel: String,
        val codeApi: String,
    ) {
        companion object {
            /** 夸克网盘 TV：与 OpenList QuarkTV 驱动同参数 */
            fun quark() = Conf(
                api = "https://open-api-drive.quark.cn",
                clientID = "d3194e61504e493eb6222857bccfed94",
                signKey = "kw2dvtd7p4t3pjl2d9ed9yc8yej8kw2d",
                appVer = "1.8.2.2",
                channel = "GENERAL",
                codeApi = "http://api.extscreen.com/quarkdrive",
            )

            /** UC 网盘 TV：与 OpenList UCTV 驱动同参数 */
            fun uc() = Conf(
                api = "https://open-api-drive.uc.cn",
                clientID = "5acf882d27b74502b7040b0c65519aa7",
                signKey = "l3srvtd7p42l0d0x1u8d7yc8ye9kki4d",
                appVer = "1.7.2.2",
                channel = "UCTVOFFICIALWEB",
                codeApi = "http://api.extscreen.com/ucdrive",
            )
        }
    }

    /** 会话数据提供者：由上层实现（DataStore 持久化） */
    interface SessionProvider {
        val deviceId: String            // 稳定设备 ID，首次生成后持久化
        var accessToken: String
        var refreshToken: String
        fun onTokensUpdated(accessToken: String, refreshToken: String)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        // 官方 TV App 伪装参数（对齐 util.go 常量）
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; U; Android 13; zh-cn; M2004J7AC Build/UKQ1.231108.001) " +
                "AppleWebKit/533.1 (KHTML, like Gecko) Mobile Safari/533.1"
        const val DEVICE_BRAND = "Xiaomi"
        const val PLATFORM = "tv"
        const val DEVICE_NAME = "M2004J7AC"
        const val DEVICE_MODEL = "M2004J7AC"
        const val BUILD_DEVICE = "M2004J7AC"
        const val BUILD_PRODUCT = "M2004J7AC"
        const val DEVICE_GPU = "Adreno (TM) 550"
        const val ACTIVITY_RECT = "{}"
        const val RESOLUTIONS = "low,normal,high,super,2k,4k"

        /** token 失效判定（对齐 util.go request 的 maybeTokenInvalid） */
        fun tokenInvalid(resp: Resp): Boolean {
            val info = resp.errorInfo.trim().lowercase()
            return (resp.status == -1 && (resp.errno == 10001 || resp.errno == 11001)) ||
                (info.contains("access token") || info.contains("access_token") ||
                    info.contains("token无效") || info.contains("token 无效"))
        }
    }

    private fun deviceParams(): Map<String, String> = mapOf(
        "app_ver" to conf.appVer,
        "device_id" to session.deviceId,
        "device_brand" to DEVICE_BRAND,
        "platform" to PLATFORM,
        "device_name" to DEVICE_NAME,
        "device_model" to DEVICE_MODEL,
        "build_device" to BUILD_DEVICE,
        "build_product" to BUILD_PRODUCT,
        "device_gpu" to DEVICE_GPU,
        "activity_rect" to ACTIVITY_RECT,
        "channel" to conf.channel,
    )

    /**
     * 签名请求核心。自动携带设备参数与 access_token；
     * token 失效时用 refresh_token 续期并重试一次。
     */
    @Throws(QuarkException::class)
    fun request(pathname: String, method: String = "GET", query: Map<String, String> = emptyMap(),
                body: JSONObject? = null, retryOnTokenExpired: Boolean = true): JSONObject {
        val ts = System.currentTimeMillis().toString()
        val url = StringBuilder(conf.api).append(pathname).append('?')
        val allQuery = LinkedHashMap<String, String>()
        allQuery["req_id"] = Sign.reqId(session.deviceId, ts)
        allQuery["access_token"] = session.accessToken
        allQuery.putAll(deviceParams())
        allQuery.putAll(query)
        allQuery.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) url.append('&')
            url.append(k).append('=').append(android.net.Uri.encode(v))
        }

        val req = Request.Builder()
            .url(url.toString())
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", USER_AGENT)
            .header("x-pan-tm", ts)
            .header("x-pan-token", Sign.xPanToken(method, pathname, ts, conf.signKey))
            .header("x-pan-client-id", conf.clientID)
            .method(
                method,
                body?.toString()?.toRequestBody("application/json".toMediaType())
                    ?: if (method == "POST") ByteArray(0).toRequestBody(null) else null,
            )
            .build()

        http.newCall(req).execute().use { respHttp ->
            val text = respHttp.body?.string() ?: ""
            if (!respHttp.isSuccessful && text.isEmpty()) {
                throw QuarkException("HTTP ${respHttp.code}")
            }
            val json = runCatching { JSONObject(text) }.orElse(JSONObject())
            val resp = Resp.from(json)
            if (tokenInvalid(resp)) {
                if (retryOnTokenExpired && session.refreshToken.isNotEmpty()) {
                    refreshByToken()
                    return request(pathname, method, query, body, retryOnTokenExpired = false)
                }
                throw QuarkException(resp.errorInfo.ifEmpty { "token 已失效，请重新扫码登录" })
            }
            if (!resp.ok) throw QuarkException(resp.errorInfo.ifEmpty { "errno=${resp.errno}" })
            return json
        }
    }

    // ---------------- 登录 ----------------

    /** 第 1 步：获取登录二维码（base64 JPEG）+ query_token */
    fun getLoginQr(): Pair<String, String> {
        val json = request(
            "/oauth/authorize",
            query = mapOf(
                "auth_type" to "code",
                "client_id" to conf.clientID,
                "scope" to "netdisk",
                "qrcode" to "1",
                "qr_width" to "460",
                "qr_height" to "460",
            ),
        )
        val data = json.optJSONObject("data") ?: json
        val qr = data.optString("qr_data")
        val queryToken = data.optString("query_token")
        if (qr.isEmpty()) throw QuarkException("获取二维码失败")
        return qr to queryToken
    }

    /** 第 2 步：轮询 query_token 换 code（未扫码/未确认时返回 null） */
    fun pollLoginCode(queryToken: String): String? {
        val json = request(
            "/oauth/code",
            query = mapOf(
                "client_id" to conf.clientID,
                "scope" to "netdisk",
                "query_token" to queryToken,
            ),
            retryOnTokenExpired = false,
        )
        val data = json.optJSONObject("data") ?: json
        val code = data.optString("code")
        return code.ifEmpty { null }
    }

    /** 第 3 步：code 或 refresh_token 换 token 对（对齐 getRefreshTokenByTV） */
    @Throws(QuarkException::class)
    fun exchangeToken(code: String, isRefresh: Boolean): TokenPair {
        val ts = System.currentTimeMillis().toString()
        val payload = JSONObject(deviceParams()).apply {
            put("req_id", Sign.reqId(session.deviceId, ts))
            put(if (isRefresh) "refresh_token" else "code", code)
        }
        val req = Request.Builder()
            .url(conf.codeApi + "/token")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            if (json.optInt("code") != 200) throw QuarkException(json.optString("message", "token 换取失败"))
            val data = json.optJSONObject("data") ?: throw QuarkException("token 换取失败：data 为空")
            val refresh = data.optString("refresh_token")
            val access = data.optString("access_token")
            if (refresh.isEmpty()) throw QuarkException("refresh token 为空")
            session.accessToken = access
            session.refreshToken = refresh
            session.onTokensUpdated(access, refresh)
            return TokenPair(access, refresh)
        }
    }

    /** token 续期 */
    fun refreshByToken() {
        val t = session.refreshToken
        if (t.isEmpty()) throw QuarkException("未登录")
        exchangeToken(t, isRefresh = true)
    }

    /** 登录状态校验（对齐 isLogin：/user?method=user_info） */
    fun checkLogin() {
        request("/user", query = mapOf("method" to "user_info"), retryOnTokenExpired = false)
    }

    // ---------------- 文件 ----------------

    /** 目录列表，自动分页取全 */
    fun list(parentFid: String, orderBy: Int = 3, desc: Boolean = true): List<QuarkFile> {
        val files = ArrayList<QuarkFile>()
        var pageIndex = 0
        val pageSize = 100
        while (true) {
            val json = request(
                "/file",
                query = mapOf(
                    "method" to "list",
                    "parent_fid" to parentFid,
                    "order_by" to orderBy.toString(),
                    "desc" to if (desc) "1" else "0",
                    "category" to "",
                    "source" to "",
                    "ex_source" to "",
                    "list_all" to "0",
                    "page_size" to pageSize.toString(),
                    "page_index" to pageIndex.toString(),
                ),
            )
            val data = json.optJSONObject("data") ?: break
            val arr = data.optJSONArray("files") ?: break
            for (i in 0 until arr.length()) files.add(QuarkFile.from(arr.getJSONObject(i)))
            val total = data.optLong("total_count", 0)
            if (pageIndex.toLong() * pageSize >= total || arr.length() == 0) break
            pageIndex++
        }
        return files
    }

    /** 原画直链（对齐 getDownloadLink） */
    fun downloadLink(fid: String): DownloadLink {
        val json = request(
            "/file",
            query = mapOf(
                "method" to "download",
                "group_by" to "source",
                "fid" to fid,
                "resolution" to RESOLUTIONS,
                "support" to "dolby_vision",
            ),
        )
        val data = json.optJSONObject("data") ?: throw QuarkException("获取直链失败")
        val url = data.optString("download_url")
        if (url.isEmpty()) throw QuarkException("获取直链失败")
        return DownloadLink(url, data.optLong("size", 0))
    }

    /** 转码多档直链 + 云续播进度（对齐 getTranscodingLink） */
    fun streamingLink(fid: String): StreamLink {
        val json = request(
            "/file",
            query = mapOf(
                "method" to "streaming",
                "group_by" to "source",
                "fid" to fid,
                "resolution" to RESOLUTIONS,
                "support" to "dolby_vision",
            ),
        )
        val data = json.optJSONObject("data") ?: throw QuarkException("获取转码链接失败")
        val arr = data.optJSONArray("video_info") ?: throw QuarkException("获取转码链接失败")
        val variants = ArrayList<StreamVariant>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("url")
            if (url.isEmpty()) continue
            variants.add(
                StreamVariant(
                    resolution = o.optString("resolution", ""),
                    url = url,
                    size = o.optLong("size", 0),
                    duration = o.optLong("duration", 0),
                    width = o.optInt("width", 0),
                    height = o.optInt("height", 0),
                ),
            )
        }
        if (variants.isEmpty()) throw QuarkException("无可播放的转码流")
        // 对齐驱动语义：按顺序取第一个可用档
        return StreamLink(
            defaultResolution = data.optString("default_resolution", ""),
            lastPlayTime = data.optLong("last_play_time", 0),
            variants = variants,
        )
    }
}

class QuarkException(message: String) : Exception(message)
