package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.CompactBgGlanceWidget
import app.aaps.ui.widget.glance.WidgetStateLoader
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * [GlanceAppWidgetReceiver] for the compact BG + IOB + COB single-row widget.
 * Mirrors [Widget] — reuses the same [WidgetStateLoader].
 */
class CompactBgWidget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var stateLoader: WidgetStateLoader
    @Inject lateinit var config: Config

    override val glanceAppWidget: GlanceAppWidget by lazy { CompactBgGlanceWidget(stateLoader, config) }

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "CompactBgWidget onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
