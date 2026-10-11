package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.glance.appwidget.updateAll
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.ui.widget.glance.AapsGlanceWidget
import app.aaps.ui.widget.glance.BgGraphGlanceWidget
import app.aaps.ui.widget.glance.CompactBgGlanceWidget
import app.aaps.ui.widget.glance.GlucoseCircleGlanceWidget
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Default [WidgetUpdater] implementation. Fires [AapsGlanceWidget.updateAll] on the
 * app-wide [CoroutineScope] so callers stay non-suspend. The widget instances are
 * stateless — they resolve their dependencies from the Metro graph inside
 * `provideGlance`, so this class doesn't need to hold any state-loader refs.
 */
// One instance: it holds the update that waits for a display to turn on, and the listener for that.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class WidgetUpdaterImpl(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    // Unqualified: @ApplicationScope is a javax qualifier and cannot appear in commonMain. The graph
    // binds the same instance under both names.
    private val scope: CoroutineScope
) : WidgetUpdater {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val gate = ScreenOffGate()

    init {
        displayManager.registerDisplayListener(
            object : DisplayManager.DisplayListener {
                override fun onDisplayChanged(displayId: Int) {
                    if (isAnyDisplayOn() && gate.displayTurnedOn()) runUpdate("DisplayOn")
                }

                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
            },
            Handler(Looper.getMainLooper())
        )
    }

    /**
     * Any display, not only the main one: a foldable can show widgets on its cover screen while the main
     * screen is off (#4608). Doze is the always-on display, which shows no home screen widgets.
     */
    private fun isAnyDisplayOn(): Boolean = displayManager.displays.any { it.state !in NOT_SHOWING_WIDGETS }

    override fun update(from: String) {
        if (!gate.request(isAnyDisplayOn())) {
            aapsLogger.debug(LTag.WIDGET, "updateWidget $from held back, no display is on")
            return
        }
        runUpdate(from)
    }

    private fun runUpdate(from: String) {
        scope.launch {
            aapsLogger.debug(LTag.WIDGET, "updateWidget $from")
            runCatching { AapsGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateWidget failed: ${it.message}", it) }
            runCatching { BgGraphGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateBgGraphWidget failed: ${it.message}", it) }
            runCatching { CompactBgGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateCompactBgWidget failed: ${it.message}", it) }
            runCatching { GlucoseCircleGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateGlucoseCircleWidget failed: ${it.message}", it) }
            runCatching { triggerSmallWidgetUpdate() }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateSmallWidget failed: ${it.message}", it) }
        }
    }

    /**
     * SmallWidget is a classic [android.appwidget.AppWidgetProvider], not Glance,
     * so we trigger its onUpdate via a broadcast targeted at the receiver.
     */
    private fun triggerSmallWidgetUpdate() {
        val component = ComponentName(context, SmallWidget::class.java)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val intent = Intent(context, SmallWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }

    private companion object {

        val NOT_SHOWING_WIDGETS = setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)
    }
}
