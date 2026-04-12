package com.autobook.cn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint

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
    com.autobook.cn.app.ui.AutobookTheme {
        com.autobook.cn.app.navigation.AutobookApp()
    }
}
