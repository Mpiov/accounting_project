package com.autobook.cn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.autobook.cn.app.sync.CloudLearningSyncScheduler
import com.autobook.cn.app.sync.NotificationListenerHealthScheduler
import com.autobook.cn.service.notification.NotificationListenerRebindHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 单 Activity 容器，承载整套 Compose 导航。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * 云端学习同步调度器。
     *
     * 用途说明：
     * - Activity 回到前台时排队刷新会员状态，避免会员开通或到期后界面长时间使用旧门禁。
     */
    @Inject
    lateinit var cloudLearningSyncScheduler: CloudLearningSyncScheduler

    /**
     * 通知监听健康检查调度器。
     *
     * 用途说明：
     * - App 回到前台时立即补一次重绑检查，减少用户打开 App 后仍漏通知的概率。
     */
    @Inject
    lateinit var notificationListenerHealthScheduler: NotificationListenerHealthScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutobookRoot()
        }
    }

    /**
     * 用途说明：
     * - App 每次回到前台时，补一次服务器会员状态刷新。
     */
    override fun onStart() {
        super.onStart()
        NotificationListenerRebindHelper.requestRebindIfPermitted(
            context = this,
            reason = "main_activity_on_start",
            force = true,
        )
        notificationListenerHealthScheduler.enqueueImmediateCheck(reason = "main_activity_on_start")
        cloudLearningSyncScheduler.enqueueForegroundMembershipRefresh()
    }
}

@Composable
private fun AutobookRoot() {
    /** 根节点统一套上主题与导航容器。 */
    com.autobook.cn.app.ui.AutobookTheme {
        com.autobook.cn.app.navigation.AutobookApp()
    }
}
