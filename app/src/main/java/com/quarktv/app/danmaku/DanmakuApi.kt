package com.quarktv.app.danmaku

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** 云搜候选弹幕结果。 */
class DanmuSearchResult(
    val name: String,
    val url: String,
    /** true=内置接口条目（url 指向 comment JSON，需再转 XML）；false=自定义接口直接返回的弹幕地址。 */
    val builtIn: Boolean = false,
)

/** 云搜结果包装：区分“成功（可能为空列表）”与“失败”。 */
class DanmuSearchListResult private constructor(
    val results: List<DanmuSearchResult>,
    val error: String,
) {
    val isError: Boolean get() = error.isNotEmpty()
    companion object {
        fun ok(results: List<DanmuSearchResult>) = DanmuSearchListResult(results, "")
        fun error(error: String) = DanmuSearchListResult(emptyList(), error)
    }
}

/**
 * 弹幕搜索 API（精简自 ubox DanmakuApi，去掉繁体 Trans/config 依赖）。
 *   - 内置 echo-danmaku：search/episodes → comment JSON → 转 bilibili XML
 *   - 自定义接口：支持 {name}/{episode} 占位 GET，或 POST name/episode 表单
 *   - 云搜 searchList：返回候选列表供用户手动选择
 * 内置流程每步带 2 次重试（退避 1.5s/3s）。
 */
object DanmakuApi {

    const val BUILTIN_API = "https://logvardanmu.konfan.cn/87654321"
    private const val MAX_RETRY = 2
    private const val RETRY_BASE_DELAY_MS = 1500L
    private const val QUERY_NUMBER = 0
    private const val QUERY_EMPTY = 1

    @Volatile
    private var searchSeq = 0

    fun cancel() { searchSeq++ }

    /** 当前生效的弹幕接口地址：自定义 > 内置。 */
    fun getApiUrl(): String {
        val custom = DanmuSettings.customApi.trim()
        return custom.ifEmpty { BUILTIN_API }
    }

    private fun hasPlaceholder(url: String) =
        url.contains("{name}") || url.contains("{episode}")

    private fun isDanmakuSearchApi(url: String): Boolean {
        var u = url.trim()
        while (u.endsWith("/")) u = u.dropLast(1)
        return u.endsWith("/danmaku")
    }

    private fun normalizeBaseUrl(url: String): String {
        var u = url.trim()
        while (u.endsWith("/")) u = u.dropLast(1)
        if (u.endsWith("/87654321")) u = u.dropLast("/87654321".length)
        return u
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** 自动搜索弹幕：返回弹幕内容（bilibili XML 或可下载地址），失败返回 null。 */
    suspend fun search(name: String, episode: String): String? {
        val apiUrl = getApiUrl()
        if (apiUrl.isEmpty()) return null
        val seq = ++searchSeq
        return try {
            if (hasPlaceholder(apiUrl)) {
                val url = apiUrl.replace("{name}", encode(name)).replace("{episode}", encode(episode))
                val body = DanmuHttp.getText(url) ?: return null
                if (seq != searchSeq) return null
                parseUrl(body).ifEmpty { null }
            } else if (isDanmakuSearchApi(apiUrl)) {
                val body = DanmuHttp.postForm(apiUrl, mapOf("name" to name, "episode" to episode)) ?: return null
                if (seq != searchSeq) return null
                parseUrl(body).ifEmpty { null }
            } else {
                searchBuiltin(apiUrl, name, episode, seq)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun searchBuiltin(apiUrl: String, rawName: String, rawEpisode: String, seq: Int): String? {
        val base = normalizeBaseUrl(apiUrl)
        val name = rawName
        val episode = rawEpisode
        var queryMode = QUERY_NUMBER
        while (true) {
            val epQuery = episodeQuery(episode, queryMode)
            val url = "$base/api/v2/search/episodes?anime=${encode(name)}" +
                if (epQuery.isEmpty()) "" else "&episode=${encode(epQuery)}"
            val body = try { getWithRetry(url) } catch (_: Exception) { null }
            if (seq != searchSeq) return null
            if (body != null) {
                val match = findEpisodeFromSearchEpisodes(body, episode)
                if (match != null && match.id.isNotEmpty()) return loadBuiltinComment(base, match.id, seq)
                if (isSearchEpisodesMovieResult(body)) return null
            }
            val next = nextQueryMode(episode, queryMode)
            if (next < 0) break
            queryMode = next
        }
        // 兜底：search/anime → bangumi
        try {
            val body = getWithRetry("$base/api/v2/search/anime?keyword=${encode(name)}")
            if (seq != searchSeq) return null
            val animeId = findAnimeId(body)
            if (animeId.isEmpty()) return null
            val bangumi = getWithRetry("$base/api/v2/bangumi/$animeId")
            if (seq != searchSeq) return null
            val match = findEpisode(bangumi, episode)
            if (match != null && match.id.isNotEmpty()) return loadBuiltinComment(base, match.id, seq)
        } catch (_: Exception) {
        }
        return null
    }

    private suspend fun loadBuiltinComment(base: String, episodeId: String, seq: Int): String? = try {
        val body = getWithRetry("$base/api/v2/comment/$episodeId?format=json")
        if (seq != searchSeq) null else commentJsonToXml(body).ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    /** 云搜：返回候选弹幕列表。 */
    suspend fun searchList(name: String, episode: String): DanmuSearchListResult {
        val apiUrl = getApiUrl()
        if (apiUrl.isEmpty()) return DanmuSearchListResult.error("弹幕搜索接口为空")
        return try {
            if (!hasPlaceholder(apiUrl) && !isDanmakuSearchApi(apiUrl)) {
                val base = normalizeBaseUrl(apiUrl)
                val body = getWithRetry("$base/api/v2/search/episodes?anime=${encode(name)}")
                return DanmuSearchListResult.ok(parseBuiltinSearchList(body, base))
            }
            val body: String = if (hasPlaceholder(apiUrl)) {
                val url = apiUrl.replace("{name}", encode(name)).replace("{episode}", encode(episode))
                DanmuHttp.getText(url) ?: throw IllegalStateException("HTTP error")
            } else {
                DanmuHttp.postForm(apiUrl, mapOf("name" to name, "episode" to episode))
                    ?: throw IllegalStateException("HTTP error")
            }
            DanmuSearchListResult.ok(parseSearchList(body))
        } catch (e: Exception) {
            DanmuSearchListResult.error(e.message?.takeIf { it.isNotEmpty() } ?: "弹幕搜索失败")
        }
    }

    /** 加载云搜选中条目：内置条目需再取 comment JSON 转 XML。 */
    suspend fun loadSearchResult(result: DanmuSearchResult): String {
        if (result.url.isEmpty()) return ""
        if (!result.builtIn) return result.url
        return try { commentJsonToXml(getWithRetry(result.url)) } catch (_: Exception) { "" }
    }

    // ---------------- HTTP（带重试） ----------------

    private suspend fun getWithRetry(url: String): String {
        var retry = 0
        while (true) {
            try {
                return DanmuHttp.getText(url) ?: throw IllegalStateException("HTTP error")
            } catch (e: Exception) {
                if (retry >= MAX_RETRY) throw e
                delay(RETRY_BASE_DELAY_MS * (retry + 1))
                retry++
            }
        }
    }

    // ---------------- 响应解析 ----------------

    private fun parseUrl(body: String): String {
        val text = body.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("[")) {
            return try {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = optStr(item, "url")
                    if (!url.isNullOrEmpty()) return url
                }
                ""
            } catch (_: Exception) { "" }
        }
        if (text.startsWith("{")) {
            return try { optStr(JSONObject(text), "url").orEmpty() } catch (_: Exception) { "" }
        }
        if (text.startsWith("http") || text.startsWith("file")) return text
        return ""
    }

    private fun parseSearchList(body: String): List<DanmuSearchResult> {
        val results = mutableListOf<DanmuSearchResult>()
        val text = body.trim()
        if (text.isEmpty()) return results
        try {
            if (text.startsWith("[")) { appendSearchResults(results, JSONArray(text)); return results }
            if (!text.startsWith("{")) return results
            val obj = JSONObject(text)
            when (val items = obj.opt("data") ?: obj.opt("list") ?: obj.opt("results")) {
                is JSONArray -> { appendSearchResults(results, items); return results }
            }
            val url = optStr(obj, "url")
            if (!url.isNullOrEmpty()) {
                results.add(DanmuSearchResult(firstStr(obj, listOf("name", "title")), url))
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun appendSearchResults(results: MutableList<DanmuSearchResult>, items: JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val url = optStr(item, "url")
            if (url.isNullOrEmpty()) continue
            results.add(DanmuSearchResult(firstStr(item, listOf("name", "title", "episode", "vod_name")), url))
        }
    }

    private fun parseBuiltinSearchList(body: String, base: String): List<DanmuSearchResult> {
        val results = mutableListOf<DanmuSearchResult>()
        if (body.trim().isEmpty()) return results
        try {
            val obj = JSONObject(body)
            appendBuiltinResults(results, base, obj, "")
            (obj.opt("bangumi") as? JSONObject)?.let {
                appendBuiltinResults(results, base, it, firstStr(it, listOf("animeTitle", "title", "name")))
            }
            for (key in listOf("animes", "anime", "data")) appendBuiltinAnimeResults(results, base, obj.opt(key))
        } catch (_: Exception) {
        }
        return results
    }

    private fun appendBuiltinAnimeResults(results: MutableList<DanmuSearchResult>, base: String, animes: Any?) {
        if (animes !is JSONArray) return
        for (i in 0 until animes.length()) {
            val anime = animes.optJSONObject(i) ?: continue
            val animeName = firstStr(anime, listOf("animeTitle", "title", "name"))
            appendBuiltinResults(results, base, anime, animeName)
            (anime.opt("bangumi") as? JSONObject)?.let { appendBuiltinResults(results, base, it, animeName) }
        }
    }

    private fun appendBuiltinResults(
        results: MutableList<DanmuSearchResult>, base: String, obj: JSONObject, animeName: String,
    ) {
        val episodes = obj.opt("episodes") as? JSONArray ?: return
        for (i in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(i) ?: continue
            val id = firstStr(ep, listOf("episodeId", "id"))
            if (id.isEmpty()) continue
            val epName = firstStr(ep, listOf("episodeTitle", "title", "name"))
            var name = if (animeName.isEmpty()) epName else "$animeName $epName"
            if (name.trim().isEmpty()) name = id
            results.add(DanmuSearchResult(name, "$base/api/v2/comment/$id?format=json", builtIn = true))
        }
    }

    private fun findAnimeId(body: String): String = try {
        val obj = JSONObject(body)
        val array = obj.opt("animes") ?: obj.opt("anime") ?: obj.opt("data")
        if (array is JSONArray && array.length() > 0) {
            array.optJSONObject(0)?.let { firstStr(it, listOf("animeId", "id")) } ?: ""
        } else ""
    } catch (_: Exception) { "" }

    private fun findEpisodeFromSearchEpisodes(body: String, episode: String): EpisodeMatch? =
        findEpisode(body, episode, allowMovieFallback = false) ?: findMovieFallback(body, episode)

    private fun isSearchEpisodesMovieResult(body: String): Boolean = try {
        val obj = JSONObject(body)
        val animes = obj.opt("animes") ?: obj.opt("anime") ?: obj.opt("data")
        if (animes !is JSONArray) false
        else (0 until animes.length()).any { i ->
            val anime = animes.optJSONObject(i)
            anime != null && isMovieType(anime) && anime.has("episodes")
        }
    } catch (_: Exception) { false }

    private fun findEpisode(body: String, episode: String, allowMovieFallback: Boolean = true): EpisodeMatch? {
        return try {
            val obj = JSONObject(body)
            val episodeList = findEpisodeList(obj) ?: return null
            if (episodeList.episodes.isEmpty()) return null
            val targetNumber = extractNumber(episode)
            var first: EpisodeMatch? = null
            var firstMandarin: EpisodeMatch? = null
            for (item in episodeList.episodes) {
                val id = firstStr(item, listOf("episodeId", "id"))
                if (id.isEmpty()) continue
                val title = firstStr(item, listOf("episodeTitle", "title", "name"))
                val number = parseEpisodeNumber(firstStr(item, listOf("episodeNumber", "number", "sort")))
                val match = EpisodeMatch(id, title, number)
                if (first == null) first = match
                if (firstMandarin == null && isMandarinTitle(title)) firstMandarin = match
                if (episode.isNotEmpty() && title.isNotEmpty() && title.contains(episode)) return match
                if (targetNumber > 0 && number == targetNumber) return match
                if (targetNumber > 0 && extractNumber(title) == targetNumber) return match
            }
            if (episode.isEmpty()) return first
            if (allowMovieFallback && episodeList.isMovie) firstMandarin ?: first else null
        } catch (_: Exception) { null }
    }

    private fun findEpisodeList(obj: JSONObject): EpisodeList? {
        (obj.opt("episodes") as? JSONArray)?.let { return EpisodeList(it.toMapList(), isMovieType(obj)) }
        (obj.opt("bangumi") as? JSONObject)?.let { bangumi ->
            (bangumi.opt("episodes") as? JSONArray)?.let { return EpisodeList(it.toMapList(), isMovieType(bangumi)) }
        }
        val animes = obj.opt("animes") ?: obj.opt("anime") ?: obj.opt("data")
        if (animes is JSONArray) {
            for (i in 0 until animes.length()) {
                val anime = animes.optJSONObject(i) ?: continue
                (anime.opt("episodes") as? JSONArray)?.let { if (it.length() > 0) return EpisodeList(it.toMapList(), isMovieType(anime)) }
            }
        }
        return null
    }

    private fun JSONArray.toMapList(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        for (i in 0 until length()) (opt(i) as? JSONObject)?.let { out.add(it) }
        return out
    }

    private fun findMovieFallback(body: String, episode: String): EpisodeMatch? {
        return try {
            val obj = JSONObject(body)
            val animes = obj.opt("animes") ?: obj.opt("anime") ?: obj.opt("data")
            if (animes !is JSONArray) return null
            val preferCantonese = episode.contains("粤语")
            var firstMovie: EpisodeMatch? = null
            for (i in 0 until animes.length()) {
                val anime = animes.optJSONObject(i) ?: continue
                if (!isMovieType(anime)) continue
                val episodes = anime.opt("episodes") as? JSONArray ?: continue
                for (j in 0 until episodes.length()) {
                    val ep = episodes.optJSONObject(j) ?: continue
                    val id = firstStr(ep, listOf("episodeId", "id"))
                    if (id.isEmpty()) continue
                    val title = firstStr(ep, listOf("episodeTitle", "title", "name"))
                    val match = EpisodeMatch(id, title, parseEpisodeNumber(firstStr(ep, listOf("episodeNumber", "number", "sort"))))
                    if (firstMovie == null) firstMovie = match
                    val preferred = if (preferCantonese) title.contains("粤语") else isMandarinTitle(title)
                    if (preferred) return match
                }
            }
            firstMovie
        } catch (_: Exception) { null }
    }

    private fun isMovieType(obj: JSONObject): Boolean {
        val type = optStr(obj, "type") ?: optStr(obj, "typeDescription")
        if (type == "电影") return true
        (obj.opt("bangumi") as? JSONObject)?.let { if (optStr(it, "type") == "电影") return true }
        val animes = obj.opt("animes") ?: obj.opt("anime") ?: obj.opt("data")
        if (animes is JSONArray) {
            for (i in 0 until animes.length()) {
                val anime = animes.optJSONObject(i)
                if (anime != null && optStr(anime, "type") == "电影") return true
            }
        }
        return false
    }

    private fun isMandarinTitle(title: String) =
        title.isNotEmpty() && (title.contains("国语") || title.contains("普通话"))

    private fun episodeQuery(episode: String, queryMode: Int): String {
        if (queryMode == QUERY_EMPTY) return ""
        val number = extractNumber(episode)
        return if (number > 0) "$number" else ""
    }

    private fun nextQueryMode(episode: String, queryMode: Int): Int =
        if (queryMode == QUERY_NUMBER && episodeQuery(episode, QUERY_NUMBER).isNotEmpty()) QUERY_EMPTY else -1

    private fun parseEpisodeNumber(value: String): Int {
        if (value.isEmpty()) return -1
        value.toDoubleOrNull()?.let { return it.toInt() }
        return extractNumber(value)
    }

    private fun extractNumber(text: String): Int {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return -1
        return digits.toIntOrNull() ?: -1
    }

    private fun firstStr(obj: JSONObject, keys: List<String>): String {
        for (key in keys) {
            val value = optStr(obj, key)
            if (!value.isNullOrEmpty()) return value
        }
        return ""
    }

    /** org.json 语义对齐：键缺失或 JSON null 返回 null（避免 "null" 字面量）。 */
    private fun optStr(obj: JSONObject?, key: String): String? {
        if (obj == null) return null
        val v = obj.opt(key)
        if (v == null || v === JSONObject.NULL) return null
        return v.toString()
    }

    // ---------------- comment JSON → bilibili XML ----------------

    /** 将内置接口的 comment JSON ({comments: [{p, m}]}) 转为 bilibili XML。 */
    fun commentJsonToXml(body: String): String {
        if (body.trim().isEmpty()) return ""
        return try {
            val obj = JSONObject(body)
            val comments = obj.opt("comments") ?: obj.opt("data")
            if (comments !is JSONArray || comments.length() == 0) return ""
            val sb = StringBuilder("<i>")
            for (i in 0 until comments.length()) {
                val item = comments.optJSONObject(i) ?: continue
                var param = optStr(item, "p") ?: ""
                var text = optStr(item, "m") ?: ""
                if (text.isEmpty()) text = optStr(item, "text") ?: ""
                if (param.isEmpty() || text.isEmpty()) continue
                sb.append("<d p=\"${escapeXml(normalizeDanmakuParam(param))}\">${escapeXml(text)}</d>")
            }
            sb.append("</i>")
            sb.toString()
        } catch (_: Exception) { "" }
    }

    private fun normalizeDanmakuParam(param: String): String {
        val values = param.split(',')
        if (values.size < 4) return param
        val time = values[0]
        val type = values[1]
        var size = values[2]
        var color = values[3]
        if (isColorValue(size)) { color = size; size = "25" }
        else if (!isColorValue(color)) color = "16777215"
        return "$time,$type,$size,${normalizeColor(color)}"
    }

    private fun isColorValue(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return false
        if (text.startsWith("#") || text.startsWith("0x") || text.startsWith("0X")) return true
        val color = text.toIntOrNull() ?: return false
        return color in 0..0x00FFFFFF
    }

    private fun normalizeColor(color: String): String {
        if (color.isEmpty()) return "16777215"
        val text = color.trim()
        return try {
            when {
                text.startsWith("#") -> Integer.parseInt(text.substring(1), 16).toString()
                text.startsWith("0x") || text.startsWith("0X") -> Integer.parseInt(text.substring(2), 16).toString()
                else -> text
            }
        } catch (_: Exception) { text }
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&apos;")
        .replace(">", "&gt;").replace("<", "&lt;")
}

private class EpisodeMatch(val id: String, val title: String, val number: Int)
private class EpisodeList(val episodes: List<JSONObject>, val isMovie: Boolean)
