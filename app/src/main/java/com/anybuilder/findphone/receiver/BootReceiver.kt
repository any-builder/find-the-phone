package com.anybuilder.findphone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anybuilder.findphone.service.MonitoringService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device boot completed, starting monitoring service")
            MonitoringService.startService(context)
        }
    }
}
