package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.BgGraphGlanceWidget
import app.aaps.ui.widget.glance.BgGraphStateLoader
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * [GlanceAppWidgetReceiver] for the BG-graph widget. Mirrors [Widget] — needed so the OS
 * can deliver system-triggered broadcasts. App-initiated refreshes go through [WidgetUpdater].
 */
class BgGraphWidget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var stateLoader: BgGraphStateLoader
    @Inject lateinit var config: Config

    override val glanceAppWidget: GlanceAppWidget by lazy { BgGraphGlanceWidget(stateLoader, config) }

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "BgGraphWidget onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
