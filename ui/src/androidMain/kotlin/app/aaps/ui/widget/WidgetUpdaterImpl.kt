package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.ui.widget.glance.AapsGlanceWidget
import app.aaps.ui.widget.glance.BgGraphGlanceWidget
import app.aaps.ui.widget.glance.CompactBgGlanceWidget
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Default [WidgetUpdater] implementation. Fires [AapsGlanceWidget.updateAll] on the
 * app-wide [CoroutineScope] so callers stay non-suspend. The widget instances are
 * stateless — they resolve their dependencies from the Metro graph inside
 * `provideGlance`, so this class doesn't need to hold any state-loader refs.
 */
// Deliberately NOT @SingleIn: the @Binds this replaces had no scope.
@ContributesBinding(AppScope::class)
class WidgetUpdaterImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    // Unqualified: @ApplicationScope is a javax qualifier and cannot appear in commonMain. The graph
    // binds the same instance under both names.
    private val scope: CoroutineScope
) : WidgetUpdater {

    override fun update(from: String) {
        scope.launch {
            aapsLogger.debug(LTag.WIDGET, "updateWidget $from")
            runCatching { AapsGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateWidget failed: ${it.message}", it) }
            runCatching { BgGraphGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateBgGraphWidget failed: ${it.message}", it) }
            runCatching { CompactBgGlanceWidget().updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateCompactBgWidget failed: ${it.message}", it) }
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
}
