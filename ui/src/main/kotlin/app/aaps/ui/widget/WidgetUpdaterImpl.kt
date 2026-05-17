package app.aaps.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.ui.widget.glance.AapsGlanceWidget
import app.aaps.ui.widget.glance.BgGraphGlanceWidget
import app.aaps.ui.widget.glance.CompactBgGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Default [WidgetUpdater] implementation. Fires [AapsGlanceWidget.updateAll] on the
 * app-wide [CoroutineScope] so callers stay non-suspend. The widget instances are
 * stateless — they resolve their dependencies via Hilt EntryPoint inside
 * `provideGlance`, so this class doesn't need to hold any state-loader refs.
 */
class WidgetUpdaterImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope
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
        }
    }
}
