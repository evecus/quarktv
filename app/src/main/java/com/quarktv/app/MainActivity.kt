package com.quarktv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.quarktv.app.data.SessionStore
import com.quarktv.app.danmaku.DanmuSettings
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.ui.theme.QuarkTheme
import com.quarktv.app.ui.theme.Screen
import com.quarktv.app.ui.browser.BrowserScreen
import com.quarktv.app.ui.login.LoginScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    lateinit var session: SessionStore
    lateinit var client: QuarkTvClient

    // true=已登录（验证通过），false=需登录；null=启动校验中
    private var loggedIn by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(applicationContext)
        session.loadBlocking()
        DanmuSettings.init(applicationContext)
        client = QuarkTvClient(session = session)

        // 首帧直接出 UI（校验中显示空屏），避免黑屏等待
        setContent {
            QuarkTheme {
                Screen {
                    when (val state = loggedIn) {
                        null -> androidx.compose.foundation.layout.Box {}
                        false -> LoginScreen(
                            client = client,
                            session = session,
                            onLoginSuccess = { loggedIn = true },
                        )
                        else -> BrowserScreen(
                            client = client,
                            session = session,
                            onLogout = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    session.logout()
                                    withContext(Dispatchers.Main) { loggedIn = false }
                                }
                            },
                        )
                    }
                }
            }
        }

        // 已有 refresh_token 则后台校验；失败（含 token 彻底过期）回登录页
        if (session.loggedIn) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = runCatching { client.checkLogin() }.isSuccess
                withContext(Dispatchers.Main) { loggedIn = ok }
            }
        } else {
            loggedIn = false
        }
    }
}
