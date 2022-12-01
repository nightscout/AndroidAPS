package info.nightscout.implementation.stats

import android.content.Context
import android.graphics.Typeface
import android.util.LongSparseArray
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.TotalDailyDose
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.R
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TddCalculatorImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val iobCobCalculator: IobCobCalculator,
    private val repository: AppRepository
) : TddCalculator {

    override fun calculate(days: Long): LongSparseArray<TotalDailyDose> {
        var startTime = MidnightTime.calc(dateUtil.now() - T.days(days).msecs())
        val endTime = MidnightTime.calc(dateUtil.now())
        //val stepSize = T.hours(24).msecs() // this is not true on DST change

        val result = LongSparseArray<TotalDailyDose>()
        // Try to load cached values
        while (startTime < endTime) {
            val tdd = repository.getCalculatedTotalDailyDose(startTime).blockingGet()
            if (tdd is ValueWrapper.Existing) result.put(startTime, tdd.value)
            else break
            //startTime += stepSize
            startTime = MidnightTime.calc(startTime + T.hours(27).msecs()) // be sure we find correct midnight
        }

        if (endTime > startTime) {
            var midnight = startTime
            while (midnight < endTime) {
                val tdd = calculate(midnight, midnight + T.hours(24).msecs())
                result.put(midnight, tdd)
                midnight = MidnightTime.calc(midnight + T.hours(27).msecs()) // be sure we find correct midnight
            }
        }
        for (i in 0 until result.size()) {
            val tdd = result.valueAt(i)
            if (tdd.interfaceIDs.pumpType != InterfaceIDs.PumpType.CACHE) {
                tdd.interfaceIDs.pumpType = InterfaceIDs.PumpType.CACHE
                aapsLogger.debug(LTag.CORE, "Storing TDD ${tdd.timestamp}")
                repository.insertTotalDailyDose(tdd)
            }
        }
        return result
    }

    override fun calculateToday(): TotalDailyDose {
        val startTime = MidnightTime.calc(dateUtil.now())
        val endTime = dateUtil.now()
        return calculate(startTime, endTime)
    }

    override fun calculateDaily(startHours: Long, endHours: Long): TotalDailyDose {
        val startTime = dateUtil.now() + T.hours(hour = startHours).msecs()
        val endTime = dateUtil.now() + T.hours(hour = endHours).msecs()
        return calculate(startTime, endTime)
    }

    override fun calculate(startTime: Long, endTime: Long): TotalDailyDose {
        val startTimeAligned = startTime - startTime % (5 * 60 * 1000)
        val endTimeAligned = endTime - endTime % (5 * 60 * 1000)
        val tdd = TotalDailyDose(timestamp = startTimeAligned)
        repository.getBolusesDataFromTimeToTime(startTime, endTime, true).blockingGet()
            .filter { it.type != Bolus.Type.PRIMING }
            .forEach { t ->
                tdd.bolusAmount += t.amount
            }
        repository.getCarbsDataFromTimeToTimeExpanded(startTime, endTime, true).blockingGet().forEach { t ->
            tdd.carbs += t.amount
        }
        val calculationStep = T.mins(5).msecs()
        for (t in startTimeAligned until endTimeAligned step calculationStep) {

            val profile = profileFunction.getProfile(t) ?: continue
            val tbr = iobCobCalculator.getBasalData(profile, t)
            val absoluteRate = tbr.tempBasalAbsolute
            tdd.basalAmount += absoluteRate / 60.0 * 5.0

            if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
                val eb = iobCobCalculator.getExtendedBolus(t)
                val absoluteEbRate = eb?.rate ?: 0.0
                tdd.bolusAmount += absoluteEbRate / 60.0 * 5.0
            }
        }
        tdd.totalAmount = tdd.bolusAmount + tdd.basalAmount
        aapsLogger.debug(LTag.CORE, tdd.toString())
        return tdd
    }

    override fun averageTDD(tdds: LongSparseArray<TotalDailyDose>): TotalDailyDose? {
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

    override fun stats(context: Context): TableLayout {
        val tdds = calculate(7)
        val averageTdd = averageTDD(tdds)
        val todayTdd = calculateToday()
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
            layout.addView(TextView(context).apply {
                text = rh.gs(R.string.today)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_HORIZONTAL
                setTextAppearance(android.R.style.TextAppearance_Material_Medium)
            })
            layout.addView(todayTdd.toTableRow(context, rh, dateUtil, includeCarbs = true))
        }
    }
}
