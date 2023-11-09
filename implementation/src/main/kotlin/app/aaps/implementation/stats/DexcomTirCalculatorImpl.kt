package app.aaps.implementation.stats

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.widget.TableLayout
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.database.impl.AppRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexcomTirCalculatorImpl @Inject constructor(
    private val profileUtil: ProfileUtil,
    private val dateUtil: DateUtil,
    private val repository: AppRepository
) : DexcomTirCalculator {

    val days = 14L

    override fun calculate(): DexcomTIR {
        val startTime = MidnightTime.calcDaysBack(days)
        val endTime = MidnightTime.calc(dateUtil.now())

        val bgReadings = repository.compatGetBgReadingsDataFromTime(startTime, endTime, true).blockingGet()
        val result = DexcomTirImpl()
        for (bg in bgReadings) result.add(bg.timestamp, bg.value)
        return result
    }

    @SuppressLint("SetTextI18n")
    override fun stats(context: Context): TableLayout =
        TableLayout(context).also { layout ->
            val tir = calculate()
            layout.layoutParams = TableLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layout.addView(tir.toRangeHeaderView(context, profileUtil))
            layout.addView(tir.toTableRowHeader(context))
            layout.addView(tir.toTableRow(context))
            layout.addView(tir.toSDView(context, profileUtil))
            layout.addView(tir.toHbA1cView(context))
        }
}