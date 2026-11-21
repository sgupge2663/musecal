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
// 対象は Roborazzi がスクショを実行する単体テストタスク（test<Variant>UnitTest）だけに
// 限定する（Codex レビュー低指摘への対応: 以前は「要求タスク名に record/compare を含むと
// 全 Test タスクの verify を外す」判定で、無関係な Test タスクにも影響していた）。
// record / compare の実行時はそちらを優先する: 要求された Roborazzi タスクが
// そのタスクと同じ variant（Debug/Release）向け、または variant なしの集約タスクの
// ときだけ、このタスクへの verify 強制を外す（プラグインがプロパティを設定する）。
tasks.withType<Test>().configureEach {
    val variant = Regex("""^test(\w+)UnitTest$""").matchEntire(name)?.groupValues?.get(1)
    if (variant != null) {
        val requestedNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
        val recordingForThisVariant = requestedNames.any { requested ->
            val lower = requested.lowercase()
            (lower.startsWith("recordroborazzi") || lower.startsWith("compareroborazzi")) &&
                (requested.endsWith(variant, ignoreCase = true) ||
                    requested.endsWith("Roborazzi", ignoreCase = true))
        }
        if (!recordingForThisVariant) {
            systemProperty("roborazzi.test.verify", "true")
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // V1-1 ログイン基盤： Credential Manager で Google ID トークンのサインイン状態を作り、
    // Calendar のアクセス権は Play services auth の AuthorizationClient で要求する（CalendarAuth.kt）。
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    testImplementation("junit:junit:4.13.2")
    // V1-2: calendarList JSON の読み取りを JVM 単体テストで検証する（android.jar の org.json はスタブのため）
    testImplementation("org.json:json:20240303")
    // EM-1: JVM スクショテスト（Roborazzi + Robolectric + Compose UI Test）
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.30.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.30.1")
}
