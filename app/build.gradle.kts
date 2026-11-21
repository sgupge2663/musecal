plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
}
