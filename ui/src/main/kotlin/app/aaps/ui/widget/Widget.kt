package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.AapsGlanceWidget
import app.aaps.ui.widget.glance.WidgetStateLoader
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * [GlanceAppWidgetReceiver] registered in the manifest. Still needed so the OS can
 * deliver system-triggered broadcasts (APPWIDGET_ENABLED, APPWIDGET_UPDATE on first placement,
 * APPWIDGET_OPTIONS_CHANGED on resize, etc.). App-initiated refreshes go through [WidgetUpdater].
 */
class Widget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var stateLoader: WidgetStateLoader
    @Inject lateinit var config: Config

    override val glanceAppWidget: GlanceAppWidget by lazy { AapsGlanceWidget(stateLoader, config) }

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
