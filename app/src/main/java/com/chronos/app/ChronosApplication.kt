package com.chronos.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import com.chronos.app.data.local.ChronosDatabase
import com.chronos.app.data.local.ChronosSettingsPrefs
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.theme.ChronosThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChronosApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { ChronosDatabase.getInstance(this) }
    val repository by lazy {
        ChronosRepository(
            customTimerDao = database.customTimerDao(),
            performanceTimerDao = database.performanceTimerDao(),
            dailyNeuralStatDao = database.dailyNeuralStatDao()
        )
    }
    val themeManager by lazy { ChronosThemeManager(this) }
    val settingsPrefs by lazy { ChronosSettingsPrefs(this) }
    val feedbackManager by lazy { com.chronos.app.feedback.CyberFeedbackManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        themeManager.applySavedNightMode()
        feedbackManager
        createNotificationChannels()
        applicationScope.launch {
            repository.ensureSeeded()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeManager.refreshResolvedPalette(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OVERLAY_ID,
                getString(R.string.overlay_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el HUD flotante de ChronosSystem"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_OVERLAY_ID = "chronos_overlay_hud_channel"
    }
}
