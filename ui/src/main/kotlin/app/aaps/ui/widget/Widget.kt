package app.aaps.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.widget.glance.AapsGlanceWidget
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * [GlanceAppWidgetReceiver] registered in the manifest. Still needed so the OS can
 * deliver system-triggered broadcasts (APPWIDGET_ENABLED, APPWIDGET_UPDATE on first placement,
 * APPWIDGET_OPTIONS_CHANGED on resize, etc.). App-initiated refreshes go through [WidgetUpdater].
 *
 * The receiver itself holds no widget state — Glance can construct it reflectively
 * (e.g. for `addAllReceiversAndProvidersToPreferences`) without injection, so
 * [glanceAppWidget] must be a no-arg instance. [AapsGlanceWidget] resolves its
 * dependencies via Hilt EntryPoint inside `provideGlance`.
 */
class Widget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override val glanceAppWidget: GlanceAppWidget = AapsGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "onReceive ${intent.action}")
        super.onReceive(context, intent)
    }
}
