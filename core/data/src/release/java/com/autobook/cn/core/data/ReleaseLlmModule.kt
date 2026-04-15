package com.autobook.cn.core.data

import android.content.Context
import com.autobook.cn.core.data.impl.LlamaCppOnDeviceLlmEngine
import com.autobook.cn.core.llm.LlamaCppRuntime
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release 变体下的真本地 LLM 绑定。
 *
 * 用途说明：
 * - 只有在 release 变体中才接入真正的 llama.cpp 运行时。
 * - 把耗时 native 构建限制在需要真机能力验证或发版的场景，减少日常 debug 迭代成本。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseLlmBindingModule {
    /**
     * 绑定 release 变体下的端侧 LLM 引擎。
     *
     * 参数说明：
     * - impl：基于 llama.cpp 的真本地引擎实现。
     */
    @Binds
    abstract fun bindOnDeviceLlmEngine(impl: LlamaCppOnDeviceLlmEngine): OnDeviceLlmEngine
}

/**
 * Release 变体下的真本地运行时实例提供器。
 *
 * 用途说明：
 * - 只有 release 变体需要解析并装载 JNI so。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReleaseLlmProvisionModule {
    /**
     * 提供真本地文本生成运行时。
     *
     * 参数说明：
     * - context：应用上下文，用于定位 native so 所在目录。
     */
    @Provides
    @Singleton
    fun provideLocalTextGenerationRuntime(
        @ApplicationContext context: Context,
    ): LocalTextGenerationRuntime = LlamaCppRuntime(context)
}
