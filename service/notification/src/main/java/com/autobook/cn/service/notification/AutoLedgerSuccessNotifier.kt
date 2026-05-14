package com.autobook.cn.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 自动入账成功通知发送器。
 *
 * 用途说明：
 * - 在通知监听服务确认账单已经写入正式账本后，向用户弹出一条可点击通知。
 * - 点击通知会打开轻松记主页，方便用户马上检查或修改刚刚自动入账的记录。
 *
 * 参数说明：
 * - context：应用上下文，用于创建通知渠道、构造 PendingIntent 和发送通知。
 */
class AutoLedgerSuccessNotifier(
    private val context: Context,
) {
    /**
     * 发送自动入账成功通知。
     *
     * 参数说明：
     * - notificationId：本地通知事件主键，用于生成稳定通知 ID，避免多条通知互相覆盖。
     * - message：流水线返回的处理摘要，可为空；当前只作为调试扩展，不直接向用户展示内部置信度。
     */
    fun notifySuccess(
        notificationId: Long,
        message: String?,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return
        }
        ensureChannel()
        NotificationManagerCompat.from(context).notify(
            buildStableNotificationId(notificationId),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("自动入账成功")
                .setContentText(buildContentText(message))
                .setContentIntent(buildOpenHomeIntent(notificationId))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    /**
     * 创建或确认通知渠道存在。
     *
     * 参数说明：
     * - 无参数；Android 8 以下系统不需要渠道。
     */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "自动入账结果",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "用于提示自动记账成功结果"
            },
        )
    }

    /**
     * 构造点击通知后打开主页的 PendingIntent。
     *
     * 参数说明：
     * - notificationId：通知事件主键，参与 requestCode，保证多条通知都能正确更新。
     */
    private fun buildOpenHomeIntent(notificationId: Long): PendingIntent {
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) {
            "Unable to resolve app launch intent"
        }.apply {
            action = ACTION_OPEN_HOME
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SOURCE_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getActivity(
            context,
            buildStableNotificationId(notificationId),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * 构造通知正文。
 *
 * 参数说明：
 * - message：流水线内部摘要；当前不展示置信度等用户不关心的信息。
 */
private fun buildContentText(message: String?): String {
    return if (message.isNullOrBlank()) {
        "已写入账本，点击查看最近记录。"
    } else {
        "已写入账本，点击查看最近记录。"
    }
}

/**
 * 为本地通知事件生成稳定系统通知 ID。
 *
 * 参数说明：
 * - notificationId：本地通知事件主键。
 */
private fun buildStableNotificationId(notificationId: Long): Int {
    return (NOTIFICATION_ID_BASE + (notificationId % NOTIFICATION_ID_RANGE)).toInt()
}

private const val CHANNEL_ID = "auto_ledger_success"
private const val ACTION_OPEN_HOME = "com.autobook.cn.action.OPEN_HOME"
private const val EXTRA_SOURCE_NOTIFICATION_ID = "source_notification_id"
private const val NOTIFICATION_ID_BASE = 6_000
private const val NOTIFICATION_ID_RANGE = 1_000
