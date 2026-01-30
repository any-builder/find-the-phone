package com.monitor.alert

import com.anybuilder.findphone.data.MonitoringConfig
import com.anybuilder.findphone.data.MonitoringState
import com.anybuilder.findphone.data.ApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun `MonitoringConfig default values are correct`() {
        val config = MonitoringConfig()
        assertEquals("", config.remoteUrl)
        assertEquals(30, config.checkIntervalSeconds)
        assertEquals("", config.targetData)
        assertFalse(config.isMonitoring)
        assertEquals(1.0f, config.volumeLevel)
        assertTrue(config.vibrateEnabled)
    }

    @Test
    fun `MonitoringConfig can be copied with new values`() {
        val config = MonitoringConfig()
        val newConfig = config.copy(
            remoteUrl = "https://example.com",
            checkIntervalSeconds = 60,
            isMonitoring = true
        )

        assertEquals("https://example.com", newConfig.remoteUrl)
        assertEquals(60, newConfig.checkIntervalSeconds)
        assertTrue(newConfig.isMonitoring)
    }

    @Test
    fun `MonitoringState default values are correct`() {
        val state = MonitoringState()
        assertFalse(state.isRunning)
        assertEquals(0L, state.lastCheckTime)
        assertEquals("", state.lastStatus)
        assertFalse(state.alertTriggered)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `ApiResponse stores data correctly`() {
        val response = ApiResponse(
            status = "success",
            data = "alert detected",
            timestamp = 1234567890L
        )

        assertEquals("success", response.status)
        assertEquals("alert detected", response.data)
        assertEquals(1234567890L, response.timestamp)
    }

    @Test
    fun `ApiResponse handles null data`() {
        val response = ApiResponse(
            status = "success",
            data = null,
            timestamp = 1234567890L
        )

        assertEquals("success", response.status)
        assertEquals(null, response.data)
        assertEquals(1234567890L, response.timestamp)
    }
}
