package com.anybuilder.findphone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.anybuilder.findphone.service.AudioPlayer

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            keyEvent?.let {
                if (it.action == KeyEvent.ACTION_DOWN && it.keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
                    val audioPlayer = AudioPlayer(context)
                    audioPlayer.stopAlert()
                }
            }
        }
    }
}
