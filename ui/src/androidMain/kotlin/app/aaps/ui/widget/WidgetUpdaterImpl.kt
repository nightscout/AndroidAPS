package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default [WidgetUpdater] implementation. Draws the Glance widgets in this process (see `render`) on
 * the app-wide [CoroutineScope] so callers stay non-suspend. The widget instances are stateless —
 * they resolve their dependencies from the Metro graph inside `provideGlance`, so this class doesn't
 * need to hold any state-loader refs.
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

    /** One render at a time: Glance does not allow two `compose` calls for the same widget at once. */
    private val renderMutex = Mutex()

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
            renderMutex.withLock {
                render(AapsGlanceWidget())
                render(BgGraphGlanceWidget())
                render(CompactBgGlanceWidget())
                render(GlucoseCircleGlanceWidget())
            }
            runCatching { triggerSmallWidgetUpdate() }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateSmallWidget failed: ${it.message}", it) }
        }
    }

    /**
     * Draws every placed instance of [widget] in this process and hands the views to the launcher.
     *
     * Not Glance's `updateAll`. That runs each update as a WorkManager job, a `SessionWorker` that
     * stays alive about 45 seconds after it has drawn - and this runs every minute while a screen is
     * on. Android counts that job time against the app's job quota, since Android 16 also with a
     * foreground service running. On one phone the widget used at least 59 minutes of the 4 hours a
     * day, and once the quota ran out the calculation and the loop stopped. `compose` runs the same
     * composition once and returns the views, with no job at all.
     *
     * Taps keep working: the widgets only use `actionStartActivity`, a plain pending intent that needs
     * no session. Glance still draws by itself when the launcher asks (a widget added or resized, the
     * daily update), which is rare.
     */
    private suspend fun render(widget: GlanceAppWidget) {
        val name = widget::class.simpleName
        try {
            val glanceManager = GlanceAppWidgetManager(context)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            for (glanceId in glanceManager.getGlanceIds(widget.javaClass)) {
                val appWidgetId = glanceManager.getAppWidgetId(glanceId)
                val views = widget.compose(context, glanceId, appWidgetManager.getAppWidgetOptions(appWidgetId))
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.WIDGET, "updateWidget $name failed: ${e.message}", e)
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
