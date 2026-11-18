package com.chronos.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chronos.app.ChronosApplication
import com.chronos.app.R
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.OverlaySettings
import com.chronos.app.data.local.PerformanceTimerEntity
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private enum class BubbleState {
        COLLAPSED_ORB,
        DAY_RESET_PILL,
        EXPANDED_LIST
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayRootView: View? = null

    private var bubbleState: BubbleState = BubbleState.COLLAPSED_ORB

    private lateinit var orbContainer: View
    private lateinit var dayResetContainer: View
    private lateinit var expandedListContainer: View
    private lateinit var ivOrbIcon: ImageView
    private lateinit var tvOrbBadge: TextView
    private lateinit var tvOverlayDayResetTime: TextView
    private lateinit var tvOverlayDayLabel: TextView
    private lateinit var tvOverlayPanelHeader: TextView
    private lateinit var timersListContainer: LinearLayout
    private lateinit var tvOverlayEmptyState: TextView
    private lateinit var btnDayPillMinimize: ImageButton
    private lateinit var btnOverlayMinimize: ImageButton

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        startAsForeground()
        setupOverlayWindow()
        startLiveSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            (application as ChronosApplication).settingsPrefs.updateSettings {
                it.copy(overlayEnabled = false)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOverlayService::class.java).apply { action = ACTION_STOP_OVERLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, ChronosApplication.CHANNEL_OVERLAY_ID)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_close, "Ocultar", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_floating_bubble, null)
        overlayRootView = view

        orbContainer = view.findViewById(R.id.bubbleOrbContainer)
        dayResetContainer = view.findViewById(R.id.bubbleDayResetContainer)
        expandedListContainer = view.findViewById(R.id.bubbleExpandedListContainer)
        ivOrbIcon = view.findViewById(R.id.ivOrbIcon)
        tvOrbBadge = view.findViewById(R.id.tvOrbBadge)
        tvOverlayDayResetTime = view.findViewById(R.id.tvOverlayDayResetTime)
        tvOverlayDayLabel = view.findViewById(R.id.tvOverlayDayLabel)
        tvOverlayPanelHeader = view.findViewById(R.id.tvOverlayPanelHeader)
        timersListContainer = view.findViewById(R.id.overlayTimersListContainer)
        tvOverlayEmptyState = view.findViewById(R.id.tvOverlayEmptyState)

        btnDayPillMinimize = view.findViewById(R.id.btnDayPillMinimize)
        btnOverlayMinimize = view.findViewById(R.id.btnOverlayMinimize)
        val btnOverlayHide = view.findViewById<ImageButton>(R.id.btnOverlayHide)

        attachDragAndTapListener(orbContainer) {
            setBubbleState(BubbleState.DAY_RESET_PILL)
        }

        attachDragAndTapListener(dayResetContainer) {
            setBubbleState(BubbleState.EXPANDED_LIST)
        }

        btnDayPillMinimize.setOnClickListener {
            setBubbleState(BubbleState.COLLAPSED_ORB)
        }
        btnOverlayMinimize.setOnClickListener {
            setBubbleState(BubbleState.COLLAPSED_ORB)
        }
        btnOverlayHide.setOnClickListener {
            (application as ChronosApplication).settingsPrefs.updateSettings {
                it.copy(overlayEnabled = false)
            }
            stopSelf()
        }

        windowManager.addView(view, layoutParams)
        setBubbleState(BubbleState.COLLAPSED_ORB)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragAndTapListener(target: View, onTap: () -> Unit) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    overlayRootView?.let { windowManager.updateViewLayout(it, layoutParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - touchStartX)
                    val dy = abs(event.rawY - touchStartY)
                    if (dx < 14f && dy < 14f) {
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setBubbleState(state: BubbleState) {
        bubbleState = state
        orbContainer.visibility = if (state == BubbleState.COLLAPSED_ORB) View.VISIBLE else View.GONE
        dayResetContainer.visibility = if (state == BubbleState.DAY_RESET_PILL) View.VISIBLE else View.GONE
        expandedListContainer.visibility = if (state == BubbleState.EXPANDED_LIST) View.VISIBLE else View.GONE
    }

    private fun startLiveSync() {
        val app = application as ChronosApplication
        val ticker = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000L)
            }
        }

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            combine(
                ticker,
                app.repository.performanceTimersFlow,
                app.repository.customTimersFlow,
                app.settingsPrefs.overlaySettings,
                app.themeManager.resolvedPalette
            ) { values ->
                val now = values[0] as Long
                @Suppress("UNCHECKED_CAST")
                val perf = values[1] as List<PerformanceTimerEntity>
                @Suppress("UNCHECKED_CAST")
                val custom = values[2] as List<CustomTimerEntity>
                val settings = values[3] as OverlaySettings
                val palette = values[4] as ResolvedCyberPalette
                OverlaySnapshot(now, perf, custom, settings, palette)
            }.collect { snap ->
                renderOverlay(snap)
            }
        }
    }

    private data class OverlaySnapshot(
        val nowMillis: Long,
        val perfTimers: List<PerformanceTimerEntity>,
        val customTimers: List<CustomTimerEntity>,
        val settings: OverlaySettings,
        val palette: ResolvedCyberPalette
    )

    private fun renderOverlay(snap: OverlaySnapshot) {
        if (overlayRootView == null) return

        val palette = snap.palette
        val density = resources.displayMetrics.density

        orbContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.glassSurfaceColor)
            setStroke((2f * density).toInt(), palette.primaryNeon)
        }
        val panelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(palette.glassSurfaceColor)
            setStroke((1.5f * density).toInt(), palette.rimHighlightColor)
        }
        dayResetContainer.background = panelBg
        expandedListContainer.background = panelBg.constantState?.newDrawable()?.mutate() ?: panelBg
        tvOrbBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.surfaceColor)
            setStroke((1.2f * density).toInt(), palette.secondaryNeon)
        }

        val millisLeft = ChronosTimeEngine.millisUntilDayReset(snap.nowMillis)
        val dayResetText = ChronosTimeEngine.formatDurationHms(millisLeft / 1000L)

        tvOverlayDayResetTime.text = dayResetText
        tvOverlayDayResetTime.setTextColor(palette.textPrimary)
        tvOverlayDayLabel.setTextColor(palette.primaryNeon)
        tvOverlayPanelHeader.setTextColor(palette.primaryNeon)
        tvOverlayEmptyState.setTextColor(palette.textSecondary)
        ivOrbIcon.imageTintList = ColorStateList.valueOf(palette.primaryNeon)
        tvOrbBadge.setTextColor(palette.secondaryNeon)
        btnDayPillMinimize.imageTintList = ColorStateList.valueOf(palette.textSecondary)
        btnOverlayMinimize.imageTintList = ColorStateList.valueOf(palette.textSecondary)

        val entries = mutableListOf<Pair<String, String>>()

        if (snap.settings.showDayReset) {
            entries.add("Reinicio del Día" to dayResetText)
        }

        if (snap.settings.showPerformanceTimers) {
            snap.perfTimers.filter { it.isRunning }.forEach { perf ->
                val rem = ChronosTimeEngine.computeLivePerformanceRemaining(perf, snap.nowMillis)
                entries.add(perf.category().title to ChronosTimeEngine.formatDurationMs(rem))
            }
        }

        if (snap.settings.showCustomTimers) {
            val projected = ChronosTimeEngine.projectTimersOnDayBar(snap.customTimers, snap.nowMillis)
            projected.filter { it.isCurrentlyActiveWindow }.forEach { node ->
                entries.add(node.timer.name to ChronosTimeEngine.formatDurationHms(node.liveRemainingSeconds))
            }
        }

        val cappedEntries = entries.take(snap.settings.maxSimultaneousTimers.coerceIn(1, 6))
        tvOrbBadge.text = cappedEntries.size.toString()

        if (bubbleState == BubbleState.EXPANDED_LIST) {
            timersListContainer.removeAllViews()
            if (cappedEntries.isEmpty()) {
                tvOverlayEmptyState.visibility = View.VISIBLE
            } else {
                tvOverlayEmptyState.visibility = View.GONE
                val inflater = LayoutInflater.from(this)
                cappedEntries.forEach { (title, formattedTime) ->
                    val row = inflater.inflate(R.layout.item_overlay_timer, timersListContainer, false)
                    val tvName = row.findViewById<TextView>(R.id.tvOverlayItemName)
                    val tvTime = row.findViewById<TextView>(R.id.tvOverlayItemTime)
                    tvName.text = title
                    tvName.setTextColor(palette.textPrimary)
                    tvTime.text = formattedTime
                    tvTime.setTextColor(palette.primaryNeon)
                    timersListContainer.addView(row)
                }
            }
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        overlayRootView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayRootView = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4091
        const val ACTION_STOP_OVERLAY = "com.chronos.app.ACTION_STOP_OVERLAY"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}
