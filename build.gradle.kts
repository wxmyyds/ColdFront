// 根构建脚本只声明插件，不在根项目应用它们。
// AGP 9.0+ 内置 Kotlin 支持，无需声明 org.jetbrains.kotlin.android。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
