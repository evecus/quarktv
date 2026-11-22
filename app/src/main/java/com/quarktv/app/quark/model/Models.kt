package com.quarktv.app.quark.model

import org.json.JSONObject

/** 通用响应头（对齐 OpenList quark_uc_tv CommonRsp/Resp） */
data class Resp(
    val status: Int,
    val errno: Int,
    val errorInfo: String,
) {
    val ok: Boolean get() = status < 400 && errno == 0

    companion object {
        fun from(json: JSONObject) = Resp(
            status = json.optInt("status", 200),
            errno = json.optInt("errno", 0),
            errorInfo = json.optString("error_info", ""),
        )
    }
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/** /file?method=list 的文件项（对齐 types.go Files） */
data class QuarkFile(
    val fid: String,
    val parentFid: String,
    val category: Int,      // 1=视频 2=音频 3=图片
    val filename: String,
    val size: Long,
    val isDir: Boolean,
    val duration: Long,     // 秒，音视频有效
    val createdAt: Long,    // ms
    val updatedAt: Long,    // ms
    val thumbnail: String,
) {
    val isVideo: Boolean get() = !isDir && category == 1
    val isAudio: Boolean get() = !isDir && category == 2

    companion object {
        fun from(o: JSONObject) = QuarkFile(
            fid = o.optString("fid"),
            parentFid = o.optString("parent_fid", "0"),
            category = o.optInt("category", 0),
            filename = o.optString("filename"),
            size = o.optLong("size", 0),
            isDir = o.optInt("isdir", 0) == 1,
            duration = o.optLong("duration", 0),
            createdAt = o.optLong("created_at", 0),
            updatedAt = o.optLong("updated_at", 0),
            thumbnail = o.optString("thumbnail_url", ""),
        )
    }
}

/** /file?method=download 响应 */
data class DownloadLink(val url: String, val size: Long)

/** /file?method=streaming 的单档清晰度 */
data class StreamVariant(
    val resolution: String,   // low/normal/high/super/2k/4k
    val url: String,
    val size: Long,
    val duration: Long,
    val width: Int,
    val height: Int,
)

/** /file?method=streaming 响应（含云续播进度） */
data class StreamLink(
    val defaultResolution: String,
    val lastPlayTime: Long,   // 秒
    val variants: List<StreamVariant>,
)
