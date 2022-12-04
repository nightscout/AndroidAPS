package info.nightscout.implementation.stats

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.LongSparseArray
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TextView
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.stats.TIR
import info.nightscout.interfaces.stats.TirCalculator
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TirCalculatorImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val repository: AppRepository
) : TirCalculator {

    override fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR> {
        if (lowMgdl < 39) throw RuntimeException("Low below 39")
        if (lowMgdl > highMgdl) throw RuntimeException("Low > High")
        val startTime = MidnightTime.calc(dateUtil.now() - T.days(days).msecs())
        val endTime = MidnightTime.calc(dateUtil.now())

        val bgReadings = repository.compatGetBgReadingsDataFromTime(startTime, endTime, true).blockingGet()
        val result = LongSparseArray<TIR>()
        for (bg in bgReadings) {
            val midnight = MidnightTime.calc(bg.timestamp)
            var tir = result[midnight]
            if (tir == null) {
                tir = TirImpl(midnight, lowMgdl, highMgdl)
                result.append(midnight, tir)
            }
            if (bg.value < 39) tir.error()
            if (bg.value >= 39 && bg.value < lowMgdl) tir.below()
            if (bg.value in lowMgdl..highMgdl) tir.inRange()
            if (bg.value > highMgdl) tir.above()
        }
        return result
    }

    private fun averageTIR(tirs: LongSparseArray<TIR>): TIR {
        val totalTir = if (tirs.size() > 0) {
            TirImpl(tirs.valueAt(0).date, tirs.valueAt(0).lowThreshold, tirs.valueAt(0).highThreshold)
        } else {
            TirImpl(7, 70.0, 180.0)
        }
        for (i in 0 until tirs.size()) {
            val tir = tirs.valueAt(i)
            totalTir.below += tir.below
            totalTir.inRange += tir.inRange
            totalTir.above += tir.above
            totalTir.error += tir.error
            totalTir.count += tir.count
        }
        return totalTir
    }

    @SuppressLint("SetTextI18n")
    override fun stats(context: Context): TableLayout =
        TableLayout(context).also { layout ->
            val lowTirMgdl = Constants.STATS_RANGE_LOW_MMOL * Constants.MMOLL_TO_MGDL
            val highTirMgdl = Constants.STATS_RANGE_HIGH_MMOL * Constants.MMOLL_TO_MGDL
            val lowTitMgdl = Constants.STATS_TARGET_LOW_MMOL * Constants.MMOLL_TO_MGDL
            val highTitMgdl = Constants.STATS_TARGET_HIGH_MMOL * Constants.MMOLL_TO_MGDL

            val tir7 = calculate(7, lowTirMgdl, highTirMgdl)
            val averageTir7 = averageTIR(tir7)
            val tir30 = calculate(30, lowTirMgdl, highTirMgdl)
            val averageTir30 = averageTIR(tir30)
            val tit7 = calculate(7, lowTitMgdl, highTitMgdl)
            val averageTit7 = averageTIR(tit7)
            val tit30 = calculate(30, lowTitMgdl, highTitMgdl)
            val averageTit30 = averageTIR(tit30)
            layout.layoutParams = TableLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layout.addView(
                TextView(context).apply {
                    text = rh.gs(info.nightscout.core.ui.R.string.tir) + " (" + Profile.toCurrentUnitsString(profileFunction, lowTirMgdl) + "-" + Profile.toCurrentUnitsString(profileFunction, highTirMgdl) + ")"
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                })
            layout.addView(TirImpl.toTableRowHeader(context, rh))
            for (i in 0 until tir7.size()) layout.addView(tir7.valueAt(i).toTableRow(context, rh, dateUtil))
            layout.addView(
                TextView(context).apply {
                    text = rh.gs(info.nightscout.core.ui.R.string.average) + " (" + Profile.toCurrentUnitsString(profileFunction, lowTirMgdl) + "-" + Profile.toCurrentUnitsString(profileFunction, highTirMgdl) + ")"
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                })
            layout.addView(averageTir7.toTableRow(context, rh, tir7.size()))
            layout.addView(averageTir30.toTableRow(context, rh, tir30.size()))
            layout.addView(
                TextView(context).apply {
                    text = rh.gs(info.nightscout.core.ui.R.string.average) + " (" + Profile.toCurrentUnitsString(profileFunction, lowTitMgdl) + "-" + Profile.toCurrentUnitsString(profileFunction, highTitMgdl) + ")"
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                })
            layout.addView(averageTit7.toTableRow(context, rh, tit7.size()))
            layout.addView(averageTit30.toTableRow(context, rh, tit30.size()))
        }
}