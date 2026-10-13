package app.aaps.ui.widget

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
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.objects.extensions.displayText
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.extensions.toVisibilityKeepSpace
import app.aaps.ui.R
import dagger.android.HasAndroidInjector
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
    @Inject lateinit var lastBgData: LastBgData
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData

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
        val alpha = preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        val useBlack = preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId)

        // Create an Intent to launch MainActivity when clicked
        val intent = Intent(context, uiInteraction.mainActivity).also { it.action = intentAction }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Widgets allow click handlers to only launch pending intents
        views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
        if (config.APS || useBlack)
            views.setInt(R.id.widget_layout, "setBackgroundColor", Color.argb(alpha, 0, 0, 0))
        else if (config.AAPSCLIENT1)
            views.setInt(R.id.widget_layout, "setBackgroundColor", Color.argb(alpha, 0xE8, 0xC5, 0x0C))
        else if (config.AAPSCLIENT2)
            views.setInt(R.id.widget_layout, "setBackgroundColor", Color.argb(alpha, 0x0F, 0xBB, 0xE0))

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
            lastBgData.lastBg()?.let { profileUtil.fromMgdlToStringInUnits(it.recalculated) } ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short))
        views.setTextColor(
            R.id.bg, when {
                lastBgData.isLow()  -> rh.gc(app.aaps.core.ui.R.color.widget_low)
                lastBgData.isHigh() -> rh.gc(app.aaps.core.ui.R.color.widget_high)
                else                -> rh.gc(app.aaps.core.ui.R.color.widget_inrange)
            }
        )
        trendCalculator.getTrendArrow(iobCobCalculator.ads)?.let {
            views.setImageViewResource(R.id.arrow, it.directionToIcon())
        }
        views.setViewVisibility(R.id.arrow, (trendCalculator.getTrendArrow(iobCobCalculator.ads) != null).toVisibilityKeepSpace())
        views.setInt(
            R.id.arrow, "setColorFilter", when {
                lastBgData.isLow()  -> rh.gc(app.aaps.core.ui.R.color.widget_low)
                lastBgData.isHigh() -> rh.gc(app.aaps.core.ui.R.color.widget_high)
                else                -> rh.gc(app.aaps.core.ui.R.color.widget_inrange)
            }
        )

        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus != null) {
            views.setTextViewText(R.id.delta, profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta))
            views.setTextViewText(R.id.avg_delta, profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.shortAvgDelta))
            views.setTextViewText(R.id.long_avg_delta, profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.longAvgDelta))
        } else {
            views.setTextViewText(R.id.delta, rh.gs(app.aaps.core.ui.R.string.value_unavailable_short))
            views.setTextViewText(R.id.avg_delta, rh.gs(app.aaps.core.ui.R.string.value_unavailable_short))
            views.setTextViewText(R.id.long_avg_delta, rh.gs(app.aaps.core.ui.R.string.value_unavailable_short))
        }

        // strike through if BG is old
        if (!lastBgData.isActualBg()) views.setInt(R.id.bg, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
        else views.setInt(R.id.bg, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)

        views.setTextViewText(R.id.time_ago, dateUtil.minOrSecAgo(rh, lastBgData.lastBg()?.timestamp))
        //views.setTextViewText(R.id.time_ago_short, "(" + dateUtil.minAgoShort(overviewData.lastBg?.timestamp) + ")")
    }

    private fun updateTemporaryBasal(views: RemoteViews) {
        views.setTextViewText(R.id.base_basal, overviewData.temporaryBasalText())
        views.setTextColor(R.id.base_basal, processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { rh.gc(app.aaps.core.ui.R.color.widget_basal) }
            ?: rh.gc(app.aaps.core.ui.R.color.white))
        views.setImageViewResource(R.id.base_basal_icon, overviewData.temporaryBasalIcon())
    }

    private fun updateExtendedBolus(views: RemoteViews) {
        val pump = activePlugin.activePump
        views.setTextViewText(R.id.extended_bolus, overviewData.extendedBolusText())
        views.setViewVisibility(R.id.extended_layout, (persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) != null && !pump.isFakingTempsByExtendedBoluses).toVisibility())
    }

    private fun bolusIob(): IobTotal = iobCobCalculator.calculateIobFromBolus().round()
    private fun basalIob(): IobTotal = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
    private fun iobText(): String =
        rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolusIob().iob + basalIob().basaliob)

    private fun updateIobCob(views: RemoteViews) {
        views.setTextViewText(R.id.iob, iobText())
        // cob
        var cobText = iobCobCalculator.getCobInfo("Overview COB").displayText(rh, decimalFormatter) ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                //only display carbsreq when carbs have not been entered recently
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += " | " + constraintsProcessed.carbsReq + " " + rh.gs(app.aaps.core.ui.R.string.required)
                }
            }
        }
        views.setTextViewText(R.id.cob, cobText)
    }

    private fun updateTemporaryTarget(views: RemoteViews) {
        val units = profileFunction.getUnits()
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        if (tempTarget != null) {
            // this is crashing, use background as text for now
            //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextWarning))
            //views.setInt(R.id.temp_target, "setBackgroundColor", rh.gc(R.color.ribbonWarning))
            views.setTextColor(R.id.temp_target, rh.gc(app.aaps.core.ui.R.color.widget_ribbonWarning))
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
                    views.setTextColor(R.id.temp_target, rh.gc(app.aaps.core.ui.R.color.widget_ribbonWarning))
                } else {
                    // this is crashing, use background as text for now
                    //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextDefault))
                    //views.setInt(R.id.temp_target, "setBackgroundColor", rh.gc(R.color.ribbonDefault))
                    views.setTextColor(R.id.temp_target, rh.gc(app.aaps.core.ui.R.color.widget_ribbonTextDefault))
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
                        rh.gc(app.aaps.core.ui.R.color.widget_ribbonWarning)
                    else rh.gc(app.aaps.core.ui.R.color.widget_ribbonTextDefault)
                } else if (it is ProfileSealed.PS) {
                    rh.gc(app.aaps.core.ui.R.color.widget_ribbonTextDefault)
                } else {
                    rh.gc(app.aaps.core.ui.R.color.widget_ribbonTextDefault)
                }
            } ?: rh.gc(app.aaps.core.ui.R.color.widget_ribbonCritical)

        views.setTextViewText(R.id.active_profile, profileFunction.getProfileNameWithRemainingTime())
        // this is crashing, use background as text for now
        //views.setInt(R.id.active_profile, "setBackgroundColor", profileBackgroundColor)
        //views.setTextColor(R.id.active_profile, profileTextColor)
        views.setTextColor(R.id.active_profile, profileTextColor)
    }

    private fun updateSensitivity(views: RemoteViews) {
        val lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Widget", aapsLogger, dateUtil)
        val lastAutosensRatio = lastAutosensData?.let { it.autosensResult.ratio * 100 }
        if (constraintChecker.isAutosensModeEnabled().value())
            views.setImageViewResource(
                R.id.sensitivity_icon,
                lastAutosensRatio?.let {
                    when {
                        it > 100.0 -> app.aaps.core.objects.R.drawable.ic_as_above
                        it < 100.0 -> app.aaps.core.objects.R.drawable.ic_as_below
                        else       -> app.aaps.core.objects.R.drawable.ic_swap_vert_black_48dp_green
                    }
                }
                    ?: app.aaps.core.objects.R.drawable.ic_swap_vert_black_48dp_green
            )
        else
            views.setImageViewResource(
                R.id.sensitivity_icon,
                lastAutosensRatio?.let {
                    when {
                        it > 100.0 -> app.aaps.core.objects.R.drawable.ic_x_as_above
                        it < 100.0 -> app.aaps.core.objects.R.drawable.ic_x_as_below
                        else       -> app.aaps.core.objects.R.drawable.ic_x_swap_vert
                    }
                }
                    ?: app.aaps.core.objects.R.drawable.ic_x_swap_vert
            )
        views.setTextViewText(R.id.sensitivity, lastAutosensData?.let {
            rh.gs(app.aaps.core.ui.R.string.autosens_short, it.autosensResult.ratio * 100)
        } ?: "")

        // Show variable sensitivity
        val request = loop.lastRun?.request
        val isfMgdl = profileFunction.getProfile()?.getProfileIsfMgdl()
        val variableSens =
            if (config.APS) request?.variableSens ?: 0.0
            else if (config.AAPSCLIENT) processedDeviceStatusData.getAPSResult()?.variableSens ?: 0.0
            else 0.0
        val ratioUsed = request?.autosensResult?.ratio ?: 1.0
        if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
            val overViewText: ArrayList<String> = ArrayList()
            if (ratioUsed != 1.0 && ratioUsed != lastAutosensData?.autosensResult?.ratio) overViewText.add(rh.gs(app.aaps.core.ui.R.string.algorithm_short, ratioUsed * 100))
            overViewText.add(
                String.format(
                    Locale.getDefault(), "%1$.1f→%2$.1f",
                    profileUtil.fromMgdlToUnits(isfMgdl, profileFunction.getUnits()),
                    profileUtil.fromMgdlToUnits(variableSens, profileFunction.getUnits())
                )
            )
            views.setTextViewText(R.id.variable_sensitivity, overViewText.joinToString("\n"))
            views.setViewVisibility(R.id.variable_sensitivity, View.VISIBLE)
        } else views.setViewVisibility(R.id.variable_sensitivity, View.GONE)
    }
}
