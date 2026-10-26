import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release 签名
// 密钥库和口令不提交到仓库，构建时从以下位置读取：
//   CI：从 GitHub Secrets 注入环境变量 COLDFRONT_KEYSTORE_*（见 .github/workflows/release.yml）
//   本机：使用 -P 或 ~/.gradle/gradle.properties 中的 coldfront.* 属性
// 环境变量优先。通过 providers 读取值，可让配置缓存跟踪凭据变化，并在口令轮换后重新计算。
// 缺少任一必填项时生成未签名 APK；fork 和 PR 即使拿不到 Secrets 也可正常编译。
fun signingValue(envName: String, propName: String): String? =
    providers.environmentVariable(envName).orNull ?: providers.gradleProperty(propName).orNull

val releaseKeystoreFile = signingValue("COLDFRONT_KEYSTORE_FILE", "coldfront.keystoreFile")
val releaseKeystorePassword = signingValue("COLDFRONT_KEYSTORE_PASSWORD", "coldfront.keystorePassword")
val releaseKeyAlias = signingValue("COLDFRONT_KEY_ALIAS", "coldfront.keyAlias")
// PKCS12 密钥库要求条目口令与密钥库口令相同；未单独设置时复用密钥库口令
val releaseKeyPassword =
    signingValue("COLDFRONT_KEY_PASSWORD", "coldfront.keyPassword") ?: releaseKeystorePassword

val releaseSigningReady = !releaseKeystoreFile.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    File(releaseKeystoreFile).isFile

if (!releaseSigningReady) {
    logger.lifecycle(
        "Release 签名未配置：assembleRelease 产出未签名 APK。" +
            "CI 检查 COLDFRONT_KEYSTORE_* Secrets，本机设置 coldfront.* Gradle 属性。"
    )
}

android {
    namespace = "io.github.wxmyyds.coldfront"
    compileSdk {
        // Compose 1.13-alpha（compose-bom-alpha）要求 compileSdk 至少为 37.1。
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.wxmyyds.coldfront"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    // 仅凭据齐全时创建 release 签名配置，避免 AGP 因配置为空而报错
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = File(releaseKeystoreFile!!) // releaseSigningReady 已保证非空
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 未配置密钥时沿用 AGP 默认行为，生成 app-release-unsigned.apk
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.miuix.nav)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material.kolor)

    // Compose 版本由 alpha BOM 管理；material3 当前为 1.5.0-alpha29。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.composables.icons.symbols.rounded)
    implementation(libs.composables.icons.symbols.rounded.filled)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Repository tests need a real JSON implementation; Android's mockable JAR contains only stubs.
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
