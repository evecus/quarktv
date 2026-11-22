import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.quarktv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.quarktv.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // 体积精简：只保留中文资源（撕掉 80+ 种语言的其他 locale 资源）
        resourceConfigurations += listOf("zh")
    }

    // 签名：正式签名走 GitHub Actions Secrets 注入的环境变量。
    // 未配置 secret 时不签名（AGP 产出 *-unsigned.apk），CI 仍可构建验证。
    signingConfigs {
        val storePath = System.getenv("SIGNING_STORE_FILE")
        val storePass = System.getenv("SIGNING_STORE_PASSWORD")
        val alias = System.getenv("SIGNING_KEY_ALIAS")
        val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
        if (!storePath.isNullOrEmpty() && !storePass.isNullOrEmpty() &&
            !alias.isNullOrEmpty() && !keyPass.isNullOrEmpty() && File(storePath).isFile
        ) {
            create("release") {
                storeFile = File(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 仅当 release signingConfig 存在时才挂上
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // 体积精简：剔除调试/无用元数据文件
    packaging {
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // 图片加载（缩略图 / 二维码）
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 网络与持久化
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 播放器（EXO 单内核；streaming 转码流可能为 HLS，带 hls 模块）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // 弹幕渲染（DanmakuFlameMaster，与 ubox/NovaTV 同库）
    implementation("com.github.ctiao:DanmakuFlameMaster:0.9.25")
}
