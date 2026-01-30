package com.anybuilder.findphone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.huawei.agconnect.AGConnectInstance
import com.huawei.agconnect.AGConnectOptionsBuilder

class FindPhoneApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "monitor_alert_channel"
    }

    override fun onCreate() {
        super.onCreate()
        AGConnectInstance.initialize(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(true)
                enableVibration(true)
                setBypassDnd(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
