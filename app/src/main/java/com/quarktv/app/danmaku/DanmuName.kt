package com.quarktv.app.danmaku

/** 从网盘文件名推断弹幕搜索用的「片名 + 集数」。 */
object DanmuName {

    /** 去掉扩展名与常见质量/来源标记，得到片名。 */
    fun title(fileName: String): String {
        var s = fileName
        // 去扩展名
        val dot = s.lastIndexOf('.')
        if (dot > 0) s = s.substring(0, dot)
        // 去常见画质/来源/编码标记（保留集数段，交给 episode 解析）
        val junk = listOf(
            "1080P", "2160P", "4K", "720P", "蓝光", "国粤双语", "国语", "粤语",
            "H265", "HEVC", "x264", "x265", "WEB-DL", "WEBDL", "BluRay", "BDRip",
            "DDP5.1", "杜比视界", "HDR", "10bit", "VIP", "TC", "HDTV",
        )
        for (j in junk) s = s.replace(j, " ", ignoreCase = true)
        // 去 S01E02 / 第xx集 / [xx] 等集数段
        s = s.replace(Regex("S\\d{1,2}E\\d{1,4}", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("第\\s*\\d{1,4}\\s*集"), " ")
        s = s.replace(Regex("[【\\[]\\s*\\d{1,4}\\s*[】\\]]"), " ")
        // 压缩空格与常见分隔符
        s = s.replace(Regex("[._\\-]+"), " ").replace(Regex("\\s+"), " ").trim()
        return s.ifEmpty { fileName.substringBefore('.') }
    }

    /** 提取集数字符串（用于弹幕匹配的 episode 参数）；电影返回空。 */
    fun episode(fileName: String): String {
        val s = fileName
        // SxxEyy → yy
        Regex("S\\d{1,2}E(\\d{1,4})", RegexOption.IGNORE_CASE).find(s)?.let { return it.groupValues[1] }
        // 第xx集
        Regex("第\\s*(\\d{1,4})\\s*集").find(s)?.let { return it.groupValues[1] }
        // [xx] / 【xx】
        Regex("[【\\[]\\s*(\\d{1,4})\\s*[】\\]]").find(s)?.let { return it.groupValues[1] }
        return ""
    }
}
