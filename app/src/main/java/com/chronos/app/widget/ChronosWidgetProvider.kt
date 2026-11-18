package com.chronos.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.chronos.app.ChronosApplication
import com.chronos.app.R
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChronosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgetsAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHRONOS_WIDGET_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ChronosWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateWidgetsAsync(context, manager, ids)
            }
        }
    }

    companion object {
        const val ACTION_CHRONOS_WIDGET_UPDATE = "com.chronos.app.ACTION_CHRONOS_WIDGET_UPDATE"

        fun requestRefresh(context: Context) {
            val intent = Intent(context, ChronosWidgetProvider::class.java).apply {
                action = ACTION_CHRONOS_WIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidgetsAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val app = context.applicationContext as? ChronosApplication
                val perfActive = app?.repository?.getPerformanceTimersSnapshot()?.count { it.isRunning } ?: 0
                val customActive = app?.repository?.getCustomTimersSnapshot()?.let { list ->
                    ChronosTimeEngine.projectTimersOnDayBar(list).count { it.isCurrentlyActiveWindow }
                } ?: 0
                val totalActive = perfActive + customActive
                val palette = app?.themeManager?.resolvedPalette?.value

                val millisLeft = ChronosTimeEngine.millisUntilDayReset()
                val baseElapsed = SystemClock.elapsedRealtime() + millisLeft

                val launchIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_chronos_lockscreen).apply {
                        setChronometer(R.id.widgetChronometer, baseElapsed, "%s", true)
                        setTextViewText(
                            R.id.widgetSubInfo,
                            "CHRONOS // ACTIVOS: $totalActive"
                        )
                        palette?.let {
                            setTextColor(R.id.widgetTitle, it.primaryNeon)
                            setTextColor(R.id.widgetChronometer, it.textPrimary)
                            setTextColor(R.id.widgetSubInfo, it.secondaryNeon)
                        }
                        setOnClickPendingIntent(R.id.widgetRoot, launchIntent)
                    }
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }
    }
}
