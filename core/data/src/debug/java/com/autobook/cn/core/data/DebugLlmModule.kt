package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.StubOnDeviceLlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug 变体下的端侧 LLM 绑定。
 *
 * 用途说明：
 * - 日常客户端调试优先走 stub，引导流程和设置页状态都还能正常联调。
 * - 避免 Android Studio 每次运行 debug 包都触发 llama.cpp 的长时间 native 编译。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugLlmModule {
    /**
     * 绑定 debug 变体下的端侧 LLM 引擎。
     *
     * 参数说明：
     * - impl：不依赖 JNI 的 stub 端侧引擎实现。
     */
    @Binds
    abstract fun bindOnDeviceLlmEngine(impl: StubOnDeviceLlmEngine): OnDeviceLlmEngine
}
