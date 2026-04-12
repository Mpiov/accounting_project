package com.autobook.cn.service.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.autobook.cn.core.data.ServiceDependenciesEntryPoint
import com.autobook.cn.core.model.NotificationEvent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutobookNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val event =
            NotificationEvent(
                packageName = sbn.packageName,
                title = extras?.getCharSequence("android.title")?.toString(),
                text = extras?.getCharSequence("android.text")?.toString(),
                postedAt = sbn.postTime,
            )

        serviceScope.launch {
            val entryPoint =
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    ServiceDependenciesEntryPoint::class.java,
                )
            val result = entryPoint.notificationIngestor().ingest(event)
            Log.d("AutobookNotifService", "Notification processed: $result")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
