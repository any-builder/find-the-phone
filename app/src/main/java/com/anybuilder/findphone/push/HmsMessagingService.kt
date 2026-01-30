package com.anybuilder.findphone.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.anybuilder.findphone.FindPhoneApp
import com.anybuilder.findphone.R
import com.anybuilder.findphone.data.Repository
import com.anybuilder.findphone.service.AudioPlayer
import com.anybuilder.findphone.service.MonitoringService

class HmsMessagingService : HmsMessageService() {

    companion object {
        private const val TAG = "HmsMessagingService"
        const val ACTION_PUSH_RECEIVED = "com.monitor.alert.PUSH_RECEIVED"
        const val EXTRA_ALERT_MESSAGE = "alert_message"
        private const val MAX_MESSAGE_AGE_MS = 5000L
    }

    private var audioPlayer: AudioPlayer? = null
    private var repository: Repository? = null
    private var alertSilencedUntil: Long = 0

    override fun onCreate() {
        super.onCreate()
        audioPlayer = AudioPlayer(this)
        repository = Repository(this)
        createNotificationChannel()
        Log.i(TAG, "HMS Messaging Service created")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "onNewToken: Token received: ${token.take(20)}...")

        val sharedPrefs = getSharedPreferences("monitor_alert_prefs", Context.MODE_PRIVATE)
        val previousToken = sharedPrefs.getString("hms_push_token", null)
        Log.i(TAG, "onNewToken: Previous token exists: ${previousToken != null}")
        Log.i(TAG, "onNewToken: New token length: ${token.length}")

        sharedPrefs.edit().putString("hms_push_token", token).apply()
        Log.i(TAG, "onNewToken: Token saved to SharedPreferences")

        repository?.let { _ ->
            Log.d(TAG, "Token updated, monitoring is always enabled")
        }
    }

    override fun onTokenError(e: Exception) {
        super.onTokenError(e)
        Log.e(TAG, "onTokenError: ${e.message}")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i(TAG, "Push message received from: ${message.from}")
        Log.i(TAG, "Message ID: ${message.messageId}")
        Log.i(TAG, "Message type: ${message.messageType}")
        Log.i(TAG, "Has notification: ${message.notification != null}")
        Log.i(TAG, "Data: ${message.dataOfMap}")

        val alertData = message.dataOfMap
        val shouldAlert = alertData["alert"]?.equals("true", ignoreCase = true) == true
        val customMessage = alertData["message"] ?: message.notification?.body ?: "Alert!"

        if (shouldAlert) {
            val messageAge = System.currentTimeMillis() - message.sentTime
            Log.d(TAG, "Message age: ${messageAge}ms")
            if (messageAge > MAX_MESSAGE_AGE_MS) {
                Log.w(TAG, "Message is too old (${messageAge}ms), ignoring alert")
                return
            }
            Log.i(TAG, "Alert triggered by push notification")
            handleAlert(customMessage)
        } else if (message.notification != null) {
            Log.d(TAG, "Notification message received, showing it")
            showNotification(customMessage)
        } else {
            Log.d(TAG, "Push received but alert flag not set")
            showNotification(customMessage)
        }
    }

    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
        Log.i(TAG, "Push message sent: $msgId")
    }

    override fun onSendError(msgId: String, error: Exception) {
        super.onSendError(msgId, error)
        Log.e(TAG, "Push send error for $msgId: ${error.message}")
    }

    private fun handleAlert(message: String) {
        if (System.currentTimeMillis() < alertSilencedUntil) {
            val remaining = (alertSilencedUntil - System.currentTimeMillis()) / 1000
            Log.d(TAG, "Alert silenced for ${remaining}s more")
            return
        }

        Log.i(TAG, "Starting foreground service for alert: $message")
        startForegroundService(message)
    }

    private fun startForegroundService(message: String) {
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_PUSH_ALERT
            putExtra(EXTRA_ALERT_MESSAGE, message)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, FindPhoneApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Monitor Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FindPhoneApp.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun silenceAlert() {
        audioPlayer?.stopAlert()
        alertSilencedUntil = System.currentTimeMillis() + 30000
        Log.i(TAG, "Alert silenced for 30 seconds")

        val handler = android.os.Handler(mainLooper)
        handler.postDelayed({
            alertSilencedUntil = 0
            Log.d(TAG, "Silence period ended")
        }, 30000)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer?.stopAlert()
        Log.i(TAG, "HMS Messaging Service destroyed")
    }
}
