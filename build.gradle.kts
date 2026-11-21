plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // EM-1: JVM スクショテスト（Roborazzi）。バージョンは app 側の適用と揃える
    id("io.github.takahirom.roborazzi") version "1.30.1" apply false
}
