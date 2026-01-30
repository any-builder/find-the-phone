package com.anybuilder.findphone.data

data class MonitoringConfig(
    val isMonitoring: Boolean = false,
    val volumeLevel: Float = 1.0f,
    val vibrateEnabled: Boolean = true,
    val ringtoneUri: String = "",
    val ringtoneName: String = "Default Alarm"
)

data class MonitoringState(
    val isRunning: Boolean = false,
    val lastCheckTime: Long = 0L,
    val lastStatus: String = "",
    val alertTriggered: Boolean = false,
    val errorMessage: String? = null
)

data class ApiResponse(
    val status: String,
    val data: String?,
    val timestamp: Long
)

data class RingtoneInfo(
    val name: String,
    val uri: String
)
