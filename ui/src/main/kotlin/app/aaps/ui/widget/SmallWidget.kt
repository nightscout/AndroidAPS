package app.aaps.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.R
import app.aaps.ui.widget.glance.WidgetDependencies
import app.aaps.ui.widget.glance.resolveClientColor
import dagger.android.HasAndroidInjector
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Classic [AppWidgetProvider] using RemoteViews/XML — not Glance — so vendor
 * lock-screen widget hosts (Samsung LockStar, etc.) that don't fully support
 * Glance can still render it.
 *
 * Layout: profile name on top, big BG + trend arrow, then time-ago / delta and
 * IOB / COB rows. Transparent background. Tap opens AAPS.
 *
 * Data is loaded via [app.aaps.ui.widget.glance.WidgetStateLoader] (same source
 * as the other widgets) inside [goAsync].
 */
class SmallWidget : AppWidgetProvider() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "SmallWidget onReceive ${intent.action}")
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependencies::class.java
        )

        CoroutineScope(Dispatchers.Default).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    val state = deps.widgetStateLoader().loadState(appWidgetId)

                    val views = RemoteViews(context.packageName, R.layout.small_widget_layout)

                    views.setTextViewText(R.id.profile_name, state.profileText)
                    views.setTextColor(R.id.profile_name, resolveClientColor(deps.config()))
                    views.setViewVisibility(
                        R.id.profile_name,
                        if (state.profileText.isNotBlank()) View.VISIBLE else View.GONE
                    )

                    views.setTextViewText(R.id.bg_value, state.bgText)
                    views.setTextColor(R.id.bg_value, state.bgColor)
                    // Strike-through for stale / non-actual BG (same semantics as Glance widget).
                    val paintFlags = if (state.strikeThrough) Paint.STRIKE_THRU_TEXT_FLAG else 0
                    views.setInt(R.id.bg_value, "setPaintFlags", paintFlags)

                    if (state.arrowResId != null) {
                        views.setImageViewResource(R.id.trend_arrow, state.arrowResId)
                        views.setInt(R.id.trend_arrow, "setColorFilter", state.bgColor)
                        views.setViewVisibility(R.id.trend_arrow, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.trend_arrow, View.GONE)
                    }

                    views.setTextViewText(R.id.time_ago, state.timeAgoText)
                    views.setTextViewText(R.id.delta, "Δ " + state.deltaText)
                    views.setTextViewText(R.id.iob, state.iobText)
                    views.setTextViewText(R.id.cob, state.cobText)

                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        val pi = PendingIntent.getActivity(context, 0, launchIntent, flags)
                        views.setOnClickPendingIntent(R.id.widget_root, pi)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (t: Throwable) {
                aapsLogger.error(LTag.WIDGET, "SmallWidget update failed: ${t.message}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
