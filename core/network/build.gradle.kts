plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.autobook.cn.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        // 说明：
        // - 当前代理环境切换到 `gemini-3-flash-preview`，避免继续命中高负载的 Pro 预览模型。
        // - 字段名统一抽象成云端图片模型，后续切换具体厂商或型号时只需要改这里。
        buildConfigField("String", "CLOUD_IMAGE_MODEL", "\"gemini-3-flash-preview\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:model"))
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
