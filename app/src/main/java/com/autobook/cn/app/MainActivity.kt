package com.autobook.cn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint

/** 单 Activity 容器，承载整套 Compose 导航。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutobookRoot()
        }
    }
}

@Composable
private fun AutobookRoot() {
    /** 根节点统一套上主题与导航容器。 */
    com.autobook.cn.app.ui.AutobookTheme {
        com.autobook.cn.app.navigation.AutobookApp()
    }
}
