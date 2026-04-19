package app.aaps.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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

class Widget : GlanceAppWidgetReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var stateLoader: WidgetStateLoader
    @Inject lateinit var config: Config

    override val glanceAppWidget: GlanceAppWidget
        get() = AapsGlanceWidget(stateLoader, config)

    companion object {

        fun updateWidget(context: Context, from: String) {
            context.sendBroadcast(Intent().also {
                it.component = ComponentName(context, Widget::class.java)
                it.putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, Widget::class.java))
                )
                it.putExtra("from", from)
                it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            })
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "onReceive ${intent.extras?.getString("from")}")
        super.onReceive(context, intent)
    }
}
