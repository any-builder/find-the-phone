package com.anybuilder.findphone.ui

import android.app.Application
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anybuilder.findphone.data.MonitoringConfig
import com.anybuilder.findphone.data.MonitoringState
import com.anybuilder.findphone.data.Repository
import com.anybuilder.findphone.service.MonitoringService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository(application)
    private val prefs: SharedPreferences = application.getSharedPreferences(
        "monitor_alert_prefs", Application.MODE_PRIVATE
    )

    private val _config = MutableStateFlow(MonitoringConfig())
    val config: StateFlow<MonitoringConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow(MonitoringState())
    val state: StateFlow<MonitoringState> = _state.asStateFlow()

    private val _pushToken = MutableStateFlow<String?>(null)
    val pushToken: StateFlow<String?> = _pushToken.asStateFlow()

    private val _isActivated = MutableStateFlow(false)
    val isActivated: StateFlow<Boolean> = _isActivated.asStateFlow()

    private val _isActivating = MutableStateFlow(false)
    val isActivating: StateFlow<Boolean> = _isActivating.asStateFlow()

    private val _activationError = MutableStateFlow<String?>(null)
    val activationError: StateFlow<String?> = _activationError.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    companion object {
        private const val KEY_ACTIVATED = "is_activated"
        private const val ACTIVATION_URL = "https://device-activate-mcvfbtcuhc.cn-hangzhou.fcapp.run"
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        Log.i("MainViewModel", "SharedPreferences changed: key=$key")
        if (key == "hms_push_token") {
            val newToken = prefs.getString("hms_push_token", null)
            Log.i("MainViewModel", "pushToken changed via listener: ${newToken?.let { "${it.substring(0, minOf(20, it.length))}..." } ?: "null"}")
            _pushToken.value = newToken
            // No longer auto-activate - user must manually activate
        } else if (key == KEY_ACTIVATED) {
            val newActivated = prefs.getBoolean(KEY_ACTIVATED, false)
            Log.i("MainViewModel", "isActivated changed via listener: $newActivated")
            _isActivated.value = newActivated
        }
    }

    init {
        Log.i("MainViewModel", "init: Loading config and push token")
        loadConfig()
        loadPushToken()
        loadActivationStatus()
        // Get device ID (ANDROID_ID), hash it, and auto-activate if needed
        val rawDeviceId = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        _deviceId.value = if (!rawDeviceId.isNullOrEmpty()) {
            hashDeviceIdToNumeric(rawDeviceId)
        } else {
            null
        }
        Log.i("MainViewModel", "Device ID (hashed): ${_deviceId.value}")
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // Manual activation only - user must click activate button
    }

    private fun loadActivationStatus() {
        _isActivated.value = prefs.getBoolean(KEY_ACTIVATED, false)
        Log.i("MainViewModel", "loadActivationStatus: isActivated=${_isActivated.value}")
    }

    private fun loadPushToken() {
        _pushToken.value = prefs.getString("hms_push_token", null)
        Log.i("MainViewModel", "loadPushToken: token=${_pushToken.value?.let { "${it.substring(0, minOf(20, it.length))}..." } ?: "null"}")
    }

    private fun loadConfig() {
        _config.value = repository.getConfig()
        _state.value = _state.value.copy(
            isRunning = MonitoringService.isServiceRunning()
        )
    }

    fun updateConfig(newConfig: MonitoringConfig) {
        viewModelScope.launch {
            repository.saveConfig(newConfig)
            _config.value = newConfig
        }
    }

    fun updateStatus(status: String) {
        _state.value = _state.value.copy(
            lastCheckTime = System.currentTimeMillis(),
            lastStatus = status
        )
    }

    fun triggerAlert() {
        _state.value = _state.value.copy(alertTriggered = true)
    }

    fun dismissAlert() {
        _state.value = _state.value.copy(alertTriggered = false)
    }

    fun activate(verifyCode: String) {
        // Manual activation with verify code from Tmall Genie
        val hashedCode = _deviceId.value
        if (hashedCode.isNullOrEmpty()) {
            _activationError.value = "Device ID not available."
            return
        }
        if (verifyCode.isEmpty()) {
            _activationError.value = "Please enter the Tmall Genie verification code."
            return
        }
        performActivation(hashedCode, verifyCode)
    }

    private fun performActivation(code: String, verifyCode: String) {
        val token = _pushToken.value
        if (token.isNullOrEmpty()) {
            _activationError.value = "Push token not available. Please wait for registration."
            return
        }

        viewModelScope.launch {
            _isActivating.value = true
            _activationError.value = null

            try {
                val success = withContext(Dispatchers.IO) {
                    performActivationRequest(code, token, verifyCode)
                }

                if (success) {
                    _isActivated.value = true
                    prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
                    Log.i("MainViewModel", "Activation successful with code: ${code.take(8)}...")
                } else {
                    _isActivated.value = false
                    prefs.edit().putBoolean(KEY_ACTIVATED, false).apply()
                    _activationError.value = "Activation failed. Please check your code and try again."
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Activation error: ${e.message}")
                _isActivated.value = false
                prefs.edit().putBoolean(KEY_ACTIVATED, false).apply()
                _activationError.value = "Activation failed: ${e.message}"
            } finally {
                _isActivating.value = false
            }
        }
    }

    private fun performActivationRequest(code: String, token: String, verifyCode: String): Boolean {
        return try {
            val url = URL(ACTIVATION_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val jsonBody = """{"code":"$code","push_token":"$token","verify_code":"$verifyCode"}"""

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            responseCode in 200..299
        } catch (e: Exception) {
            Log.e("MainViewModel", "Network error during activation: ${e.message}")
            false
        }
    }

    fun clearPushToken() {
        Log.i("MainViewModel", "clearPushToken: called")
        _pushToken.value = null
        prefs.edit().remove("hms_push_token").apply()
    }

    private fun hashDeviceIdToNumeric(deviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(deviceId.toByteArray())
        // Convert hash to numeric string (take first 8 digits)
        val numericString = hashBytes.joinToString("") { byte ->
            val unsigned = byte.toInt() and 0xFF
            String.format("%03d", unsigned % 1000)
        }
        return numericString.substring(0, minOf(8, numericString.length))
    }
}
