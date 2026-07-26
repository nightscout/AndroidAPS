package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.CompactBgGlanceWidget
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * [GlanceAppWidgetReceiver] for the compact BG + IOB + COB single-row widget.
 * Mirrors [Widget]. See [Widget] for why widget state is resolved inside
 * `provideGlance` rather than injected into the receiver.
 */
class CompactBgWidget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override val glanceAppWidget: GlanceAppWidget = CompactBgGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "CompactBgWidget onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
