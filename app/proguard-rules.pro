# 保留数据模型（Gson/反射场景，okhttp 自身不用反射但 JSONObject 解析不依赖保留）
# 这里只放真正需要的规则，避免保留过多导致 R8 无法缩小体积

# OkHttp 平台类型用于反射检测（Android 上可安全移除警告）
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# Kotlinx coroutines 调试探针（已通过 packagingOptions 排除）
-dontwarn kotlinx.coroutines.debug.**

# DanmakuFlameMaster：库内部大量类用于 XML/反射与 JNI，整体保留，避免 R8 剥离后崩溃
-keep class master.flame.danmaku.** { *; }
-dontwarn master.flame.danmaku.**
