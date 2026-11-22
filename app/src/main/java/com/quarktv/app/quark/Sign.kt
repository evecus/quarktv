package com.quarktv.app.quark

import java.security.MessageDigest

/** 请求签名工具（对齐 OpenList quark_uc_tv util.go generateReqSign） */
object Sign {

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).toHex()

    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).toHex()

    /** x-pan-token = SHA256(method & pathname & timestamp & signKey) */
    fun xPanToken(method: String, pathname: String, timestamp: String, signKey: String): String =
        sha256Hex("$method&$pathname&$timestamp&$signKey")

    /** req_id = MD5(deviceId + timestamp) */
    fun reqId(deviceId: String, timestamp: String): String = md5Hex(deviceId + timestamp)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
