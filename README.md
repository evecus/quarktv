# QuarkTV（夸克网盘 TV 客户端）

参考 OpenList `quark_uc_tv` 驱动协议实现的第三方夸克网盘 Android TV 客户端。
Kotlin + Jetpack Compose TV + Media3 EXO 单内核，minSdk 21。

## 功能

- **扫码登录**：TV 全屏显示二维码，手机夸克 App 扫码确认，refresh_token 持久化 + access_token 自动续期
- **网盘浏览**：目录树导航、缩略图卡片、视频时长角标、遥控器焦点导航（返回时恢复上次焦点位置）
- **视频播放**：原画直链（download）或多档转码流（streaming，low→4K），按 ▲ 呼出清晰度菜单；
  streaming 模式读取 `last_play_time` 云续播
- **音乐播放**：直链播放，进度/时长显示
- **弹幕**：进入视频自动按文件名搜索弹幕（片名+集数），播放页右上角「搜弹幕」按钮可云搜候选并手动选择；
  渲染用 DanmakuFlameMaster，支持开关/行数/速度/字号/透明度/随机颜色/自定义接口
- **设置**：播放方式（原画/转码）、默认清晰度、弹幕设置、退出登录

## 协议（对齐 OpenList drivers/quark_uc_tv）

| 项 | 值 |
|---|---|
| API | `https://open-api-drive.quark.cn` |
| 签名 | `x-pan-token = SHA256(method&path&timestamp&signKey)`，`req_id = MD5(deviceId+ts)` |
| 登录 | `/oauth/authorize`（QR）→ `/oauth/code`（轮询）→ `{codeApi}/token`（code/refresh_token 换发） |
| 目录 | `GET /file?method=list`（100/页自动分页） |
| 播放 | `GET /file?method=download`（原画）/ `method=streaming`（转码+续播进度） |

token 换发中转走 `api.extscreen.com/quarkdrive`（extscreen 第三方服务，与 OpenList 一致）。

## 弹幕（对齐 ubox-master / danmu-anywhere 插件）

| 项 | 说明 |
|---|---|
| 渲染 | `com.github.ctiao:DanmakuFlameMaster:0.9.25`，视频层上叠加透明 `DanmakuView`，seek/暂停/缓冲与 EXO 时间轴联动 |
| 数据源 | 内置 `https://logvardanmu.konfan.cn/87654321`（与 danmu-anywhere 插件同源）：`search/episodes` → `comment/{id}?format=json` → 转 bilibili XML；支持 `{name}/{episode}` 占位或 `/danmaku` POST 自定义接口 |
| 自动搜 | 进入视频按文件名推断片名+集数自动搜索（`DanmuName`），结果存于与视图解耦的 Manager，重组/全屏不丢 |
| 手动搜 | 播放页「搜弹幕」按钮 → 云搜候选列表 → 点选加载 |
| 设置 | 设置弹窗内「弹幕设置」区：开关/行数/速度/字号/透明度/随机颜色/内置或自定义接口，改后即时热更新（DataStore 持久化） |

## 构建与签名

GitHub Actions 推送 `master`/`main` 即构建，产物在 Artifacts：
- `quarktv-debug`：调试版（debug key 签名）
- `quarktv-release`：发布版（配置了签名 secret 时为 `QuarkTV-release-signed.apk`；未配置时为 `app-release-unsigned.apk`）

### 配置正式签名（GitHub Secrets）

1. 本地生成 keystore（已有可跳过）：

```bash
keytool -genkeypair -v -keystore quarktv.jks -keyalg RSA -keysize 2048 -validity 10000 -alias quarktv
```

2. 生成 Base64：

```bash
base64 -w0 quarktv.jks    # Linux
# 或 macOS: base64 -i quarktv.jks | tr -d '\n'
```

3. 在 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret** 添加 4 个 secret：

| Secret | 值 |
|---|---|
| `KEYSTORE_BASE64` | 上一步输出的 Base64 字符串 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名，如 `quarktv` |
| `KEY_PASSWORD` | key 密码（与 keystore 密码相同也可） |

4. 重新触发工作流（push 或手动 Run workflow），`quarktv-release` 里即为正式签名 APK。

> secret 只用于 CI 内临时解码，不写入代码、不进日志。

## 体积精简措施

| 项 | 说明 |
|---|---|
| R8 完整模式 | `android.enableR8.fullMode=true`，深度剥离无用代码 |
| 资源收缩 | release 开启 `isShrinkResources`，`resourceConfigurations = ["zh"]` 只保留中文资源 |
| 精简打包 | 排除 `*.kotlin_module`、`DebugProbesKt.bin`、LICENSE/NOTICE 等无用元数据 |
| 依赖精简 | `ui-tooling-preview` 仅 debugImplementation；无 lifecycle-runtime-compose 冗余依赖 |
| 图标收敛 | 从官方 APK 提取的单张 512px 图标，按 5 档密度缩放（无多套冗余切图） |

## 已知边界

- TV 接口只读：不支持上传/删除/重命名/移动（与官方 TV 端一致）
- 播放进度只读（续播可恢复），回写接口未逆向
- signKey / client_id 为社区逆向常量，若失效需更新 `QuarkTvClient.Conf`
