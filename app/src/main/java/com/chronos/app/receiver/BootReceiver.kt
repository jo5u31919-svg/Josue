package com.chronos.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.chronos.app.ChronosApplication
import com.chronos.app.service.FloatingOverlayService
import com.chronos.app.widget.ChronosWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ChronosWidgetProvider.requestRefresh(context)
            val app = context.applicationContext as? ChronosApplication ?: return
            val overlayEnabled = app.settingsPrefs.currentSnapshot().overlayEnabled
            if (overlayEnabled && Settings.canDrawOverlays(context)) {
                FloatingOverlayService.start(context)
            }
        }
    }
}
