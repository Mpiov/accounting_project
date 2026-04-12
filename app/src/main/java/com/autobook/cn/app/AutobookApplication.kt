package com.autobook.cn.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** 应用入口，负责初始化 Hilt 依赖注入。 */
@HiltAndroidApp
class AutobookApplication : Application()
