package app.aaps.implementation.overview

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.R
import app.aaps.core.objects.extensions.convertedToPercent
import app.aaps.core.objects.extensions.isInProgress
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.core.objects.extensions.toStringShort
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
class OverviewDataImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
    private val processedTbrEbData: ProcessedTbrEbData
) : OverviewData {

    // Initialize the window anchor so workers reading these fields before the
    // first PreparePredictionsWorker run see sensible values. Rounded to next
    // full hour + GraphView-era 100ms nudge to avoid axis-label rounding.
    override var toTime: Long = initialToTime()
    override var fromTime: Long = toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
    override var endTime: Long = toTime

    private fun initialToTime(): Long {
        val tz = TimeZone.currentSystemDefault()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val local = now.toLocalDateTime(tz)
        val truncatedHour = LocalDateTime(local.year, local.month, local.day, local.hour, 0)
        val nextFullHour = truncatedHour.toInstant(tz).plus(1, DateTimeUnit.HOUR, tz)
        return nextFullHour.toEpochMilliseconds() + 100000
    }

    override fun temporaryBasalText(): String =
        runBlocking { profileFunction.getProfile() }?.let { profile ->
            var temporaryBasal = processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())
            if (temporaryBasal?.isInProgress == false) temporaryBasal = null
            temporaryBasal?.let { rh.gs(app.aaps.core.ui.R.string.temp_basal_overview_short_name) + " " + it.toStringShort(rh) }
                ?: rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())
        } ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

    override fun temporaryBasalDialogText(): String =
        runBlocking { profileFunction.getProfile() }?.let { profile ->
            processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { temporaryBasal ->
                "${rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label)}: ${rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())}" +
                    "\n" + rh.gs(app.aaps.core.ui.R.string.tempbasal_label) + ": " + temporaryBasal.toStringFull(profile, dateUtil, rh)
            }
                ?: "${rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label)}: ${rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())}"
        } ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

    @DrawableRes override fun temporaryBasalIcon(): Int =
        runBlocking { profileFunction.getProfile() }?.let { profile ->
            processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { temporaryBasal ->
                val percentRate = temporaryBasal.convertedToPercent(dateUtil.now(), profile)
                when {
                    percentRate > 100 -> R.drawable.ic_cp_basal_tbr_high
                    percentRate < 100 -> R.drawable.ic_cp_basal_tbr_low
                    else              -> R.drawable.ic_cp_basal_no_tbr
                }
            }
        } ?: R.drawable.ic_cp_basal_no_tbr

    @AttrRes override fun temporaryBasalColor(context: Context?): Int =
        processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let {
            rh.gac(context, app.aaps.core.ui.R.attr.basal)
        } ?: rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)

    override fun extendedBolusText(): String =
        runBlocking { persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) }?.let { extendedBolus ->
            if (!extendedBolus.isInProgress(dateUtil)) ""
            else if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, extendedBolus.rate)
            else ""
        } ?: ""

    override fun extendedBolusDialogText(): String =
        runBlocking { persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) }?.toStringFull(dateUtil, rh) ?: ""
}
