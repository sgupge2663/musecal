plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // EM-1: JVM スクショテスト（Roborazzi + Robolectric）
    id("io.github.takahirom.roborazzi")
}

// 署名鍵の固定（タスクA）: 環境変数 MUSECAL_DEBUG_KEYSTORE が既存ファイルを指す場合のみ、
// 共通デバッグ鍵で debug に署名する。未設定なら従来の自動生成デバッグ署名にフォールバック。
val sharedDebugKeystorePath = System.getenv("MUSECAL_DEBUG_KEYSTORE")
val useSharedDebugKeystore =
    !sharedDebugKeystorePath.isNullOrBlank() && File(sharedDebugKeystorePath).isFile

android {
    namespace = "jp.hayashida.musecal"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.hayashida.musecal"
        minSdk = 26 // java.time を desugar なしで使うため
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        if (useSharedDebugKeystore) {
            create("sharedDebug") {
                storeFile = File(sharedDebugKeystorePath!!)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug {
            if (useSharedDebugKeystore) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            // EM-1: Robolectric が assets（sample_events.json）とリソースを読むために必要
            isIncludeAndroidResources = true
            all {
                // ネイティブ描画＋1080x2400 のビットマップを扱うためテスト JVM のヒープを広げる
                it.maxHeapSize = "2g"
                // Robolectric が実行時に android-all を取得する際、ビルド環境（サンドボックスの
                // 中継プロキシ等）の JVM 設定をテスト JVM にも引き継ぐ。CI では未設定なので何も起きない
                for (key in listOf(
                    "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
                    "javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword",
                )) {
                    System.getProperty(key)?.let { v -> it.systemProperty(key, v) }
                }
            }
        }
    }
}

// EM-1: Roborazzi はプロパティ未指定だとキャプチャが無効（検証されない）ため、
// 通常のテスト実行（check.sh / CI の testDebugUnitTest）では verify を既定にする。
// record / compare の各タスク実行時はそちらを優先する（プラグインがプロパティを設定する）。
tasks.withType<Test>().configureEach {
    val requested = gradle.startParameter.taskNames.joinToString(" ")
    if (!requested.contains("record", ignoreCase = true) &&
        !requested.contains("compare", ignoreCase = true)
    ) {
        systemProperty("roborazzi.test.verify", "true")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    // EM-1: JVM スクショテスト（Roborazzi + Robolectric + Compose UI Test）
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.30.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.30.1")
}
