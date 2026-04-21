package app.aaps.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.ui.widget.glance.AapsGlanceWidget
import app.aaps.ui.widget.glance.BgGraphGlanceWidget
import app.aaps.ui.widget.glance.BgGraphStateLoader
import app.aaps.ui.widget.glance.CompactBgGlanceWidget
import app.aaps.ui.widget.glance.WidgetStateLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Default [WidgetUpdater] implementation. Fires [AapsGlanceWidget.updateAll] on the
 * app-wide [CoroutineScope] so callers stay non-suspend.
 */
class WidgetUpdaterImpl @Inject constructor(
    private val context: Context,
    private val stateLoader: WidgetStateLoader,
    private val bgGraphStateLoader: BgGraphStateLoader,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope
) : WidgetUpdater {

    override fun update(from: String) {
        scope.launch {
            aapsLogger.debug(LTag.WIDGET, "updateWidget $from")
            runCatching { AapsGlanceWidget(stateLoader, config).updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateWidget failed: ${it.message}", it) }
            runCatching { BgGraphGlanceWidget(bgGraphStateLoader, config).updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateBgGraphWidget failed: ${it.message}", it) }
            runCatching { CompactBgGlanceWidget(stateLoader, config).updateAll(context) }
                .onFailure { aapsLogger.error(LTag.WIDGET, "updateCompactBgWidget failed: ${it.message}", it) }
        }
    }
}
