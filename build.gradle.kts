// 顶层构建脚本 — 仅声明插件，不在此 apply
// 注意：AGP 9.0+ 内置 Kotlin 支持，不再声明 org.jetbrains.kotlin.android
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
