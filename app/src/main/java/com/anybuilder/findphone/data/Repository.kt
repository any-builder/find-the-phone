package com.anybuilder.findphone.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class Repository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "monitor_alert_prefs"
        private const val KEY_CONFIG = "monitoring_config"
    }

    fun saveConfig(config: MonitoringConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_CONFIG, json).apply()
    }

    fun getConfig(): MonitoringConfig {
        val json = prefs.getString(KEY_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, MonitoringConfig::class.java)
            } catch (e: Exception) {
                MonitoringConfig()
            }
        } else {
            MonitoringConfig()
        }
    }
}
