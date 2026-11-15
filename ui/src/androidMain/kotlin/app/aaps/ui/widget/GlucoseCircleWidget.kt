package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.GlucoseCircleGlanceWidget
import dev.zacsweers.metro.Inject

/** Receiver for [GlucoseCircleGlanceWidget]. Same setup as [Widget]. */
class GlucoseCircleWidget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override val glanceAppWidget: GlanceAppWidget = GlucoseCircleGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        aapsLogger.debug(LTag.WIDGET, "GlucoseCircleWidget onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
