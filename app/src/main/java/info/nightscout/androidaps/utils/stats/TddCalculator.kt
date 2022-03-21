package info.nightscout.androidaps.utils.stats

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.LongSparseArray
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.toTableRow
import info.nightscout.androidaps.extensions.toTableRowHeader
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class TddCalculator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
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

        val calculationStep = T.mins(5).msecs()
        val tempBasals = iobCobCalculator.getTempBasalIncludingConvertedExtendedForRange(startTime, endTime, calculationStep)
        for (t in startTime until endTime step calculationStep) {
            val midnight = MidnightTime.calc(t)
            val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
            val tbr = tempBasals[t]
            val profile = profileFunction.getProfile(t) ?: continue
            val absoluteRate = tbr?.convertedToAbsolute(t, profile) ?: profile.getBasal(t)
            tdd.basalAmount += absoluteRate / T.mins(60).msecs().toDouble() * calculationStep.toDouble()

            if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
                // they are not included in TBRs
                val eb = iobCobCalculator.getExtendedBolus(t)
                val absoluteEbRate = eb?.rate ?: 0.0
                tdd.bolusAmount += absoluteEbRate / T.mins(60).msecs().toDouble() * calculationStep.toDouble()
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

    fun calculateDaily(): TotalDailyDose {
        val startTime = MidnightTime.calc(dateUtil.now())
        val endTime = dateUtil.now()
        val tdd = TotalDailyDose(timestamp = startTime)
        //val result = TotalDailyDose()
        repository.getBolusesDataFromTimeToTime(startTime, endTime, true).blockingGet()
            .filter { it.type != Bolus.Type.PRIMING }
            .forEach { t ->
                //val midnight = MidnightTime.calc(t.timestamp)
                //val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
                tdd.bolusAmount += t.amount
                //result.put(midnight, tdd)
            }
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, true).blockingGet().forEach { t ->
            //val midnight = MidnightTime.calc(t.timestamp)
            //val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
            tdd.carbs += t.amount
            //result.put(midnight, tdd)
        }
        val calculationStep = T.mins(5).msecs()
        for (t in startTime until endTime step calculationStep) {

            //val midnight = MidnightTime.calc(t)
            //val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
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
            //result.put(midnight, tdd)
        }
        //for (i in 0 until tdd.size()) {
        //val tdd = result.valueAt(i)
        tdd.totalAmount = tdd.bolusAmount + tdd.basalAmount
        //}

        aapsLogger.debug(LTag.CORE, tdd.toString())
        return tdd
    }

    fun calculate24Daily(): TotalDailyDose {
        val startTime = dateUtil.now() - T.hours(hour = 24).msecs()
        val endTime = dateUtil.now()
        val tdd = TotalDailyDose(timestamp = startTime)
        //val result = TotalDailyDose()
        repository.getBolusesDataFromTimeToTime(startTime, endTime, true).blockingGet()
            .filter { it.type != Bolus.Type.PRIMING }
            .forEach { t ->
                //val midnight = MidnightTime.calc(t.timestamp)
                //val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
                tdd.bolusAmount += t.amount
                //result.put(midnight, tdd)
            }
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, true).blockingGet().forEach { t ->
            //val midnight = MidnightTime.calc(t.timestamp)
            //val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
            tdd.carbs += t.amount
            //result.put(midnight, tdd)
        }
        val calculationStep = T.mins(5).msecs()
        //val tempBasals = iobCobCalculator.getTempBasalIncludingConvertedExtendedForRange(startTime, endTime, calculationStep)
        for (t in startTime until endTime step calculationStep) {

            //val midnight = MidnightTime.calc(t)
            //val tdd = result[midnight] ?: TotalDailyDose(timestamp = midnight)
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
            //result.put(midnight, tdd)
        }
        //for (i in 0 until tdd.size()) {
        //val tdd = result.valueAt(i)
        tdd.totalAmount = tdd.bolusAmount + tdd.basalAmount
        //}

        aapsLogger.debug(LTag.CORE, tdd.toString())
        return tdd
    }

    fun averageTDD(tdds: LongSparseArray<TotalDailyDose>): TotalDailyDose? {
        val totalTdd = TotalDailyDose(timestamp = dateUtil.now())
        if (tdds.size() == 0) return null
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

    @SuppressLint("SetTextI18n")
    fun stats(context: Context): TableLayout {
        val tdds = calculate(7)
        val averageTdd = averageTDD(tdds)
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
        return TableLayout(context).also { layout ->
            layout.layoutParams = TableLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layout.addView(TextView(context).apply {
                text = rh.gs(R.string.tdd)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_HORIZONTAL
                setTextAppearance(android.R.style.TextAppearance_Material_Medium)
            })
            layout.addView(TotalDailyDose.toTableRowHeader(context, rh, includeCarbs = true))
            for (i in 0 until tdds.size()) layout.addView(tdds.valueAt(i).toTableRow(context, rh, dateUtil, includeCarbs = true))
            averageTdd?.let { averageTdd ->
                layout.addView(TextView(context).apply {
                    layoutParams = lp
                    text = rh.gs(R.string.average)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                })
                layout.addView(averageTdd.toTableRow(context, rh, tdds.size(), includeCarbs = true))
            }
        }
    }
}