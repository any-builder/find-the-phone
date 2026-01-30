package com.anybuilder.findphone.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AudioPlayer(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    init {
        setupVibrator()
    }

    private fun setupVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun playAlert(
        volumeLevel: Float = 1.0f,
        vibrate: Boolean = true,
        ringtoneUri: String = ""
    ) {
        try {
            Log.i(TAG, "Playing alert audio. Volume: ${(volumeLevel * 100).toInt()}%, Vibrate: $vibrate, Ringtone: $ringtoneUri")

            val audioUri = if (ringtoneUri.isNotBlank()) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }

            Log.d(TAG, "Using audio URI: $audioUri")

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setVolume(volumeLevel, volumeLevel)
                isLooping = true
                prepare()
                start()
            }

            Log.i(TAG, "Alert audio started successfully")

            if (vibrate) {
                Log.d(TAG, "Starting vibration")
                vibrateDevice()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alert: ${e.message}")
            e.printStackTrace()

            try {
                Log.d(TAG, "Falling back to default alarm ringtone")
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, defaultUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setVolume(volumeLevel, volumeLevel)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.i(TAG, "Fallback alert audio started successfully")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to play fallback alert: ${e2.message}")
                e2.printStackTrace()
            }
        }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500, 200, 500),
                        0
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopAlert() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                    Log.i(TAG, "Alert audio stopped")
                }
                release()
            }
            mediaPlayer = null
            vibrator?.cancel()
            Log.d(TAG, "Alert stopped and resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop alert: ${e.message}")
            e.printStackTrace()
        }
    }

    fun setAudioMode() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
            Log.d(TAG, "Audio mode set to max alarm volume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set audio mode: ${e.message}")
            e.printStackTrace()
        }
    }

    fun previewRingtone(ringtoneUri: String, volumeLevel: Float = 0.5f) {
        try {
            Log.i(TAG, "Previewing ringtone: $ringtoneUri")

            val audioUri = if (ringtoneUri.isNotBlank()) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }

            stopAlert()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setVolume(volumeLevel, volumeLevel)
                isLooping = false
                prepare()
                start()
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopAlert()
            }, 5000)

            Log.i(TAG, "Ringtone preview started for 5 seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preview ringtone: ${e.message}")
            e.printStackTrace()
        }
    }
}
