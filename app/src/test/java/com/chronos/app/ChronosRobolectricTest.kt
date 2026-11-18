package com.chronos.app

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chronos.app.ui.MainActivity
import com.chronos.app.ui.OverclockActivity
import com.chronos.app.ui.custom.CoreTemperatureView
import com.chronos.app.ui.custom.NeuralPulseChartView
import com.chronos.app.ui.custom.OverclockOverloadFrameView
import com.chronos.app.ui.fragment.StatsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChronosRobolectricTest {

    @Test
    fun `app name matches ChronosSystem`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ChronosSystem", appName)
    }

    @Test
    fun `bottom navigation switches to StatsFragment with NeuralPulseChartView`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        assertNotNull(bottomNav)

        bottomNav.selectedItemId = R.id.nav_stats
        activity.supportFragmentManager.executePendingTransactions()

        val fragmentContainer = activity.findViewById<View>(R.id.fragmentContainer)
        assertEquals(View.VISIBLE, fragmentContainer.visibility)

        val statsFragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        assertTrue(statsFragment is StatsFragment)

        val chart = activity.findViewById<NeuralPulseChartView>(R.id.neuralPulseChart)
        assertNotNull(chart)

        bottomNav.selectedItemId = R.id.nav_home
        assertEquals(View.GONE, fragmentContainer.visibility)
    }

    @Test
    fun `main activity launches OverclockActivity from launcher card`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val launcherCard = activity.findViewById<View>(R.id.cardOverclockLauncher)
        assertNotNull(launcherCard)
        launcherCard.performClick()

        val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(OverclockActivity::class.java.name, nextIntent.component?.className)
    }

    @Test
    fun `overclock activity switches block duration and shows instability warning on pause`() {
        val controller = Robolectric.buildActivity(OverclockActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull(activity.findViewById<OverclockOverloadFrameView>(R.id.overloadFrameView))
        assertNotNull(activity.findViewById<CoreTemperatureView>(R.id.coreTemperatureView))

        val chip50 = activity.findViewById<Chip>(R.id.chipBlock50)
        chip50.performClick()

        val countdown = activity.findViewById<TextView>(R.id.tvOverclockCountdown)
        assertEquals("50:00", countdown.text.toString())

        val startPauseBtn = activity.findViewById<MaterialButton>(R.id.btnStartPauseOverclock)
        startPauseBtn.performClick() // Starts Overclock
        assertEquals(activity.getString(R.string.overclock_pause), startPauseBtn.text.toString())

        startPauseBtn.performClick() // Pauses -> triggers instability warning
        val warningCard = activity.findViewById<View>(R.id.cardInstabilityWarning)
        assertEquals(View.VISIBLE, warningCard.visibility)
        assertEquals("ESTAB. 90%", activity.findViewById<TextView>(R.id.tvStabilityBadge).text.toString())
    }

    @Test
    fun `cyber feedback manager toggles persist and methods execute safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.chronos.app.feedback.CyberFeedbackManager.getInstance(context)

        assertTrue(manager.isSoundEnabled())
        assertTrue(manager.isHapticsEnabled())

        manager.setSoundEnabled(false)
        manager.setHapticsEnabled(false)
        assertEquals(false, manager.isSoundEnabled())
        assertEquals(false, manager.isHapticsEnabled())

        manager.setSoundEnabled(true)
        manager.setHapticsEnabled(true)
        assertTrue(manager.isSoundEnabled())
        assertTrue(manager.isHapticsEnabled())

        manager.playTerminalClick()
        manager.playBeepProgress()
        manager.playErrorGlitch()
        manager.playSuccessChime(withHaptic = true)
        manager.hapticOverclockAlert()
    }
}
