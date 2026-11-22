package com.quarktv.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quarktv.app.quark.QuarkTvClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

private val Context.dataStore by preferencesDataStore(name = "quarktv")

/**
 * 会话持久化：refresh_token / access_token / device_id / 偏好设置。
 * 同时实现 QuarkTvClient.SessionProvider（token 自动续期回写）。
 */
class SessionStore(private val context: Context) : QuarkTvClient.SessionProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override var deviceId: String = ""
        private set

    override var accessToken: String = ""
    override var refreshToken: String = ""

    // 偏好
    var linkMethod: String = "download"    // download 原画 / streaming 转码
    var preferredResolution: String = ""   // 空=自动；low/normal/high/super/2k/4k

    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyDevice = stringPreferencesKey("device_id")
    private val keyLinkMethod = stringPreferencesKey("link_method")
    private val keyResolution = stringPreferencesKey("resolution")

    private fun newDeviceId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 启动时同步加载（仅启动一次的阻塞读取） */
    fun loadBlocking() = runBlocking {
        val p = context.dataStore.data.first()
        deviceId = p[keyDevice] ?: newDeviceId().also { persistRaw("device_id", it) }
        refreshToken = p[keyRefresh] ?: ""
        accessToken = p[keyAccess] ?: ""
        linkMethod = p[keyLinkMethod] ?: "download"
        preferredResolution = p[keyResolution] ?: ""
        Unit
    }

    private fun persistRaw(key: String, value: String) {
        scope.launch {
            context.dataStore.edit {
                it[stringPreferencesKey(key)] = value
            }
        }
    }

    override fun onTokensUpdated(accessToken: String, refreshToken: String) {
        persistRaw("access_token", accessToken)
        persistRaw("refresh_token", refreshToken)
    }

    fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        onTokensUpdated(access, refresh)
    }

    fun saveLinkMethod(value: String) {
        linkMethod = value
        persistRaw("link_method", value)
    }

    fun savePreferredResolution(value: String) {
        preferredResolution = value
        persistRaw("resolution", value)
    }

    suspend fun logout() {
        accessToken = ""
        refreshToken = ""
        context.dataStore.edit {
            it.remove(keyAccess)
            it.remove(keyRefresh)
        }
    }

    val loggedIn: Boolean get() = refreshToken.isNotEmpty()
}
