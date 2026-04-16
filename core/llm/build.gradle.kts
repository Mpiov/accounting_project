plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.autobook.cn.core.llm"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            // 说明：优先覆盖真机 arm64，其次保留 x86_64 方便后续模拟器联调。
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // 说明：保持与官方 llama.android 示例接近的编译开关，优先保证 GGUF CPU 推理可用。
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                // 说明：Android 上 nativeLibraryDir 不一定可枚举到 APK 内 so，关闭动态发现后改为构建期静态注册 CPU backend，更稳。
                arguments += "-DGGML_BACKEND_DL=OFF"
                // 说明：静态 backend 模式下保留单一 CPU backend，避免继续生成依赖动态发现的多变体 so。
                arguments += "-DGGML_CPU_ALL_VARIANTS=OFF"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            // 说明：release 需要编译完整本地 LLM 能力，这里对齐 local.properties 指向的 E 盘 CMake 版本。
            version = "3.31.11"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
