plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.autobook.cn.service.notification"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
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
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    // Gradle 9 下需要显式补充 JUnit Platform launcher，保证单测执行器可正常启动。
    testRuntimeOnly(libs.junit.platform.launcher)
}
