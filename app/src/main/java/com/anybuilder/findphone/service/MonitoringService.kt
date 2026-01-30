package com.anybuilder.findphone.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anybuilder.findphone.FindPhoneApp
import com.anybuilder.findphone.R
import com.anybuilder.findphone.data.Repository
import com.anybuilder.findphone.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.TimeUnit

class MonitoringService : Service() {

    companion object {
        private const val TAG = "MonitoringService"
        const val ACTION_START = "com.monitor.alert.START_MONITORING"
        const val ACTION_STOP = "com.monitor.alert.STOP_MONITORING"
        const val ACTION_SILENCE = "com.monitor.alert.SILENCE_ALERT"
        const val ACTION_PUSH_ALERT = "com.monitor.alert.PUSH_ALERT"
        private var isRunning = false
        private const val ALERT_TIMEOUT_MS = 15000L
        private const val FOREGROUND_NOTIFICATION_ID = 1

        fun isServiceRunning(): Boolean = isRunning

        fun startService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }

        fun silenceAlert(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_SILENCE
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var isAlertPlaying: Boolean = false
    private var isServiceActivated: Boolean = false
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var repository: Repository
    private var alertTimeoutHandler: android.os.Handler? = null
    private var alertTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MonitoringService created")
        repository = Repository(this)
        audioPlayer = AudioPlayer(this)
        alertTimeoutHandler = android.os.Handler(mainLooper)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Received START command")
                isServiceActivated = true
                startMonitoring()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Received STOP command")
                isServiceActivated = false
                stopMonitoring()
            }
            ACTION_SILENCE -> {
                Log.i(TAG, "Received SILENCE command - silencing alert")
                silenceAlert()
            }
            ACTION_PUSH_ALERT -> {
                val message = intent.getStringExtra("alert_message") ?: "Alert!"
                Log.i(TAG, "Received PUSH ALERT command: $message")
                if (!isRunning) {
                    startForegroundWithNotification()
                }
                handlePushAlert(message)
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        Log.i(TAG, "Starting monitoring service")
        isRunning = true
        startForegroundWithNotification()
    }

    private fun stopMonitoring() {
        Log.i(TAG, "Stopping alert, keeping service running")
        audioPlayer.stopAlert()
        cancelAlertTimeout()
        isAlertPlaying = false
        updateNotificationForMonitoringState()
    }

    private fun startForegroundWithNotification() {
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FindPhoneApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止响铃", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MonitorAlert::MonitoringWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun scheduleAlertTimeout() {
        cancelAlertTimeout()
        alertTimeoutRunnable = Runnable {
            Log.i(TAG, "Alert timeout reached (15s), stopping alert")
            audioPlayer.stopAlert()
            isAlertPlaying = false
            updateNotificationForMonitoringState()
            Log.d(TAG, "Alert stopped after timeout, service continues running")
        }
        alertTimeoutHandler?.postDelayed(alertTimeoutRunnable!!, ALERT_TIMEOUT_MS)
        Log.d(TAG, "Alert timeout scheduled for ${ALERT_TIMEOUT_MS}ms")
    }

    private fun cancelAlertTimeout() {
        alertTimeoutRunnable?.let { runnable ->
            alertTimeoutHandler?.removeCallbacks(runnable)
            Log.d(TAG, "Alert timeout cancelled")
        }
        alertTimeoutRunnable = null
    }

    private fun updateNotificationForMonitoringState() {
        if (isServiceActivated && isRunning) {
            val notification = createPersistentNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun silenceAlert() {
        audioPlayer.stopAlert()
        cancelAlertTimeout()
        isAlertPlaying = false
        Log.i(TAG, "Alert silenced, service continues running")
        updateNotificationForMonitoringState()
    }

    private fun shouldPlayAlert(): Boolean = true

    private fun handlePushAlert(message: String) {
        Log.i(TAG, "Handling push alert: $message")

        val config = repository.getConfig()

        if (shouldPlayAlert()) {
            Log.i(TAG, "Playing alert for push notification")
            audioPlayer.setAudioMode()
            audioPlayer.playAlert(
                volumeLevel = config.volumeLevel,
                vibrate = config.vibrateEnabled,
                ringtoneUri = config.ringtoneUri
            )
            isAlertPlaying = true
            scheduleAlertTimeout()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved called - restarting service")
        val restartIntent = Intent(applicationContext, MonitoringService::class.java).apply {
            action = ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MonitoringService destroyed")
        isRunning = false
        serviceScope.cancel()
        audioPlayer.stopAlert()
        cancelAlertTimeout()
        alertTimeoutHandler = null
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
    }
}
