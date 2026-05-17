package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.BgGraphGlanceWidget
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * [GlanceAppWidgetReceiver] for the BG-graph widget. Mirrors [Widget].
 * See [Widget] for why widget state is resolved inside `provideGlance` rather
 * than injected into the receiver.
 */
class BgGraphWidget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override val glanceAppWidget: GlanceAppWidget = BgGraphGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "BgGraphWidget onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
