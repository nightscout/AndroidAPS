package info.nightscout.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.widget.RemoteViews
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.directionToIcon
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.iob.displayText
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.interfaces.end
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.aps.VariableSensitivityResult
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.TrendCalculator
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.extensions.toVisibilityKeepSpace
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.ui.R
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * Implementation of App Widget functionality.
 */
class Widget : AppWidgetProvider() {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var sp: SP
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var decimalFormatter: DecimalFormatter

    companion object {

        // This object doesn't behave like singleton,
        // many threads were created. Making handler static resolve this issue
        private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

        fun updateWidget(context: Context, from: String) {
            context.sendBroadcast(Intent().also {
                it.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, Widget::class.java)))
                it.putExtra("from", from)
                it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            })
        }
    }

    private val intentAction = "OpenApp"

    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        aapsLogger.debug(LTag.WIDGET, "onReceive ${intent?.extras?.getString("from")}")
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val alpha = sp.getInt(WidgetConfigureActivity.PREF_PREFIX_KEY + appWidgetId, WidgetConfigureActivity.DEFAULT_OPACITY)

        // Create an Intent to launch MainActivity when clicked
        val intent = Intent(context, uiInteraction.mainActivity).also { it.action = intentAction }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Widgets allow click handlers to only launch pending intents
        views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
        views.setInt(R.id.widget_layout, "setBackgroundColor", Color.argb(alpha, 0, 0, 0))

        handler.post {
            if (config.appInitialized) {
                updateBg(views)
                updateTemporaryBasal(views)
                updateExtendedBolus(views)
                updateIobCob(views)
                updateTemporaryTarget(views)
                updateProfile(views)
                updateSensitivity(views)
                // Instruct the widget manager to update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun updateBg(views: RemoteViews) {
        views.setTextViewText(
            R.id.bg,
            overviewData.lastBg(iobCobCalculator.ads)?.let { profileUtil.fromMgdlToStringInUnits(it.value) } ?: rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short))
        views.setTextColor(
            R.id.bg, when {
                overviewData.isLow(iobCobCalculator.ads)  -> rh.gc(info.nightscout.core.ui.R.color.widget_low)
                overviewData.isHigh(iobCobCalculator.ads) -> rh.gc(info.nightscout.core.ui.R.color.widget_high)
                else                                      -> rh.gc(info.nightscout.core.ui.R.color.widget_inrange)
            }
        )
        trendCalculator.getTrendArrow(iobCobCalculator.ads)?.let {
            views.setImageViewResource(R.id.arrow, it.directionToIcon())
        }
        views.setViewVisibility(R.id.arrow, (trendCalculator.getTrendArrow(iobCobCalculator.ads) != null).toVisibilityKeepSpace())
        views.setInt(
            R.id.arrow, "setColorFilter", when {
                overviewData.isLow(iobCobCalculator.ads)  -> rh.gc(info.nightscout.core.ui.R.color.widget_low)
                overviewData.isHigh(iobCobCalculator.ads) -> rh.gc(info.nightscout.core.ui.R.color.widget_high)
                else                                      -> rh.gc(info.nightscout.core.ui.R.color.widget_inrange)
            }
        )

        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus != null) {
            views.setTextViewText(R.id.delta, profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta))
            views.setTextViewText(R.id.avg_delta, profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.shortAvgDelta))
            views.setTextViewText(R.id.long_avg_delta, profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.longAvgDelta))
        } else {
            views.setTextViewText(R.id.delta, rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short))
            views.setTextViewText(R.id.avg_delta, rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short))
            views.setTextViewText(R.id.long_avg_delta, rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short))
        }

        // strike through if BG is old
        if (!overviewData.isActualBg(iobCobCalculator.ads)) views.setInt(R.id.bg, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
        else views.setInt(R.id.bg, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)

        views.setTextViewText(R.id.time_ago, dateUtil.minAgo(rh, overviewData.lastBg(iobCobCalculator.ads)?.timestamp))
        //views.setTextViewText(R.id.time_ago_short, "(" + dateUtil.minAgoShort(overviewData.lastBg?.timestamp) + ")")
    }

    private fun updateTemporaryBasal(views: RemoteViews) {
        views.setTextViewText(R.id.base_basal, overviewData.temporaryBasalText(iobCobCalculator))
        views.setTextColor(R.id.base_basal, iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { rh.gc(info.nightscout.core.ui.R.color.widget_basal) }
            ?: rh.gc(info.nightscout.core.ui.R.color.white))
        views.setImageViewResource(R.id.base_basal_icon, overviewData.temporaryBasalIcon(iobCobCalculator))
    }

    private fun updateExtendedBolus(views: RemoteViews) {
        val pump = activePlugin.activePump
        views.setTextViewText(R.id.extended_bolus, overviewData.extendedBolusText(iobCobCalculator))
        views.setViewVisibility(R.id.extended_layout, (iobCobCalculator.getExtendedBolus(dateUtil.now()) != null && !pump.isFakingTempsByExtendedBoluses).toVisibility())
    }

    private fun updateIobCob(views: RemoteViews) {
        views.setTextViewText(R.id.iob, overviewData.iobText(iobCobCalculator))
        // cob
        var cobText = overviewData.cobInfo(iobCobCalculator).displayText(rh, decimalFormatter) ?: rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short)

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                //only display carbsreq when carbs have not been entered recently
                if (overviewData.lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += " | " + constraintsProcessed.carbsReq + " " + rh.gs(info.nightscout.core.ui.R.string.required)
                }
            }
        }
        views.setTextViewText(R.id.cob, cobText)
    }

    private fun updateTemporaryTarget(views: RemoteViews) {
        val units = profileFunction.getUnits()
        val tempTarget = overviewData.temporaryTarget
        if (tempTarget != null) {
            // this is crashing, use background as text for now
            //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextWarning))
            //views.setInt(R.id.temp_target, "setBackgroundColor", rh.gc(R.color.ribbonWarning))
            views.setTextColor(R.id.temp_target, rh.gc(info.nightscout.core.ui.R.color.widget_ribbonWarning))
            views.setTextViewText(
                R.id.temp_target,
                profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units) + " " + dateUtil.untilString(tempTarget.end, rh)
            )
        } else {
            // If the target is not the same as set in the profile then oref has overridden it
            profileFunction.getProfile()?.let { profile ->
                val targetUsed = loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0

                if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                    aapsLogger.debug("Adjusted target. Profile: ${profile.getTargetMgdl()} APS: $targetUsed")
                    views.setTextViewText(R.id.temp_target, profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units))
                    // this is crashing, use background as text for now
                    //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextWarning))
                    //views.setInt(R.id.temp_target, "setBackgroundResource", rh.gc(R.color.tempTargetBackground))
                    views.setTextColor(R.id.temp_target, rh.gc(info.nightscout.core.ui.R.color.widget_ribbonWarning))
                } else {
                    // this is crashing, use background as text for now
                    //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextDefault))
                    //views.setInt(R.id.temp_target, "setBackgroundColor", rh.gc(R.color.ribbonDefault))
                    views.setTextColor(R.id.temp_target, rh.gc(info.nightscout.core.ui.R.color.widget_ribbonTextDefault))
                    views.setTextViewText(R.id.temp_target, profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units))
                }
            }
        }
    }

    private fun updateProfile(views: RemoteViews) {
        val profileTextColor =
            profileFunction.getProfile()?.let {
                if (it is ProfileSealed.EPS) {
                    if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                        rh.gc(info.nightscout.core.ui.R.color.widget_ribbonWarning)
                    else rh.gc(info.nightscout.core.ui.R.color.widget_ribbonTextDefault)
                } else if (it is ProfileSealed.PS) {
                    rh.gc(info.nightscout.core.ui.R.color.widget_ribbonTextDefault)
                } else {
                    rh.gc(info.nightscout.core.ui.R.color.widget_ribbonTextDefault)
                }
            } ?: rh.gc(info.nightscout.core.ui.R.color.widget_ribbonCritical)

        views.setTextViewText(R.id.active_profile, profileFunction.getProfileNameWithRemainingTime())
        // this is crashing, use background as text for now
        //views.setInt(R.id.active_profile, "setBackgroundColor", profileBackgroundColor)
        //views.setTextColor(R.id.active_profile, profileTextColor)
        views.setTextColor(R.id.active_profile, profileTextColor)
    }

    private fun updateSensitivity(views: RemoteViews) {
        if (constraintChecker.isAutosensModeEnabled().value())
            views.setImageViewResource(R.id.sensitivity_icon, info.nightscout.core.main.R.drawable.ic_swap_vert_black_48dp_green)
        else
            views.setImageViewResource(R.id.sensitivity_icon, info.nightscout.core.main.R.drawable.ic_x_swap_vert)
        views.setTextViewText(R.id.sensitivity, overviewData.lastAutosensData(iobCobCalculator)?.let { autosensData ->
            String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100)
        } ?: "")

        // Show variable sensitivity
        val request = loop.lastRun?.request
        if (request is VariableSensitivityResult) {
            val isfMgdl = profileFunction.getProfile()?.getIsfMgdl()
            val variableSens = request.variableSens
            if (variableSens != isfMgdl && variableSens != null && isfMgdl != null) {
                views.setTextViewText(
                    R.id.variable_sensitivity,
                    String.format(
                        Locale.getDefault(), "%1$.1f→%2$.1f",
                        profileUtil.fromMgdlToUnits(isfMgdl),
                        profileUtil.fromMgdlToUnits(variableSens)
                    )
                )
                views.setViewVisibility(R.id.variable_sensitivity, View.VISIBLE)
            } else views.setViewVisibility(R.id.variable_sensitivity, View.GONE)
        } else views.setViewVisibility(R.id.variable_sensitivity, View.GONE)
    }
}