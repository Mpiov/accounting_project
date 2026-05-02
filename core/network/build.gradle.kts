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
        // 说明：
        // - 当前后端只面向 App 联调，不对普通用户开放页面入口，因此先直连腾讯云公网 IP。
        // - 后续绑定域名和 HTTPS 后，只需要替换这里的基础地址。
        buildConfigField("String", "AUTOBOOK_BACKEND_BASE_URL", "\"http://122.152.220.19/\"")
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
    
    api(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
