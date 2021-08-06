package info.nightscout.androidaps.utils.stats

import android.text.Spanned
import android.util.LongSparseArray
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.toText
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class TddCalculator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val iobCobCalculator: IobCobCalculator,
    private val repository: AppRepository
) {

    fun calculate(days: Long): LongSparseArray<TotalDailyDose> {
        val startTime = MidnightTime.calc(dateUtil.now() - T.days(days).msecs())
        val endTime = MidnightTime.calc(dateUtil.now())

        val result = LongSparseArray<TotalDailyDose>()
        repository.getBolusesDataFromTimeToTime(startTime, endTime, true).blockingGet()
            .filter { it.type != Bolus.Type.PRIMING }
            .forEach { t ->
                val midnight = MidnightTime.calc(t.timestamp)
                val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
                tdd.bolusAmount += t.amount
                result.put(midnight, tdd)
            }
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, true).blockingGet().forEach { t ->
            val midnight = MidnightTime.calc(t.timestamp)
            val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
            tdd.carbs += t.amount
            result.put(midnight, tdd)
        }

        for (t in startTime until endTime step T.mins(5).msecs()) {
            val midnight = MidnightTime.calc(t)
            val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
            val tbr = iobCobCalculator.getTempBasalIncludingConvertedExtended(t)
            val profile = profileFunction.getProfile(t) ?: continue
            val absoluteRate = tbr?.convertedToAbsolute(t, profile) ?: profile.getBasal(t)
            tdd.basalAmount += absoluteRate / 60.0 * 5.0

            if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
                // they are not included in TBRs
                val eb = iobCobCalculator.getExtendedBolus(t)
                val absoluteEbRate = eb?.rate ?: 0.0
                tdd.bolusAmount += absoluteEbRate / 60.0 * 5.0
            }
            result.put(midnight, tdd)
        }
        for (i in 0 until result.size()) {
            val tdd = result.valueAt(i)
            tdd.totalAmount = tdd.bolusAmount + tdd.basalAmount
        }
        aapsLogger.debug(LTag.CORE, result.toString())
        return result
    }

    private fun averageTDD(tdds: LongSparseArray<TotalDailyDose>): TotalDailyDose {
        val totalTdd = TotalDailyDose(timestamp = dateUtil.now())
        for (i in 0 until tdds.size()) {
            val tdd = tdds.valueAt(i)
            totalTdd.basalAmount += tdd.basalAmount
            totalTdd.bolusAmount += tdd.bolusAmount
            totalTdd.totalAmount += tdd.totalAmount
            totalTdd.carbs += tdd.carbs
        }
        totalTdd.basalAmount /= tdds.size().toDouble()
        totalTdd.bolusAmount /= tdds.size().toDouble()
        totalTdd.totalAmount /= tdds.size().toDouble()
        totalTdd.carbs /= tdds.size().toDouble()
        return totalTdd
    }

    fun stats(): Spanned {
        val tdds = calculate(7)
        val averageTdd = averageTDD(tdds)
        return HtmlHelper.fromHtml(
            "<b>" + resourceHelper.gs(R.string.tdd) + ":</b><br>" +
                toText(tdds, true) +
                "<b>" + resourceHelper.gs(R.string.average) + ":</b><br>" +
                averageTdd.toText(resourceHelper, tdds.size(), true)
        )
    }

    @Suppress("SameParameterValue")
    private fun toText(tdds: LongSparseArray<TotalDailyDose>, includeCarbs: Boolean): String {
        var t = ""
        for (i in 0 until tdds.size()) {
            t += "${tdds.valueAt(i).toText(resourceHelper, dateUtil, includeCarbs)}<br>"
        }
        return t
    }
}