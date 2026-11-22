package com.quarktv.app.ui.login

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quarktv.app.data.SessionStore
import com.quarktv.app.quark.QuarkTvClient
import com.quarktv.app.ui.theme.QuarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 扫码登录页：TV 全屏显示二维码，手机夸克 App 扫码确认。
 * 3s 轮询 code，成功后换取 token 并回调。
 */
@Composable
fun LoginScreen(
    client: QuarkTvClient,
    session: SessionStore,
    onLoginSuccess: () -> Unit,
) {
    var qrImage by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("正在获取二维码…") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val (qrBase64, queryToken) = client.getLoginQr()
                val pure = qrBase64.substringAfter("base64,", qrBase64)
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                withContext(Dispatchers.Main) {
                    qrImage = bmp
                    status = "请用手机夸克 App 扫码登录"
                }
                // 轮询：未扫码/未确认时接口报错，视为继续等待
                val deadline = System.currentTimeMillis() + 120_000
                while (System.currentTimeMillis() < deadline) {
                    delay(3_000)
                    val code = runCatching { client.pollLoginCode(queryToken) }.getOrNull()
                    if (!code.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) { status = "登录成功，正在进入…" }
                        client.exchangeToken(code, isRefresh = false)
                        withContext(Dispatchers.Main) { onLoginSuccess() }
                        return@withContext
                    }
                }
                withContext(Dispatchers.Main) { status = "二维码已过期，请按返回键重试" }
            }.onFailure { e ->
                withContext(Dispatchers.Main) { status = "登录失败：${e.message}" }
            }
        }
    }

    Column(
        modifier = Modifier.padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("夸克网盘", style = MaterialTheme.typography.displaySmall,
            color = QuarkColors.TextPrimary)

        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(QuarkColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = qrImage
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "登录二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(300.dp),
                )
            } else {
                Text("二维码加载中…", color = QuarkColors.TextSecondary)
            }
        }

        Text(status, color = QuarkColors.TextSecondary, textAlign = TextAlign.Center)
    }
}
