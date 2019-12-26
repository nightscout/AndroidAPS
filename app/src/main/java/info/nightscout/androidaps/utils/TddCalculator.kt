package info.nightscout.androidaps.utils

import android.text.Spanned
import android.util.LongSparseArray
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.TDD
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import org.slf4j.LoggerFactory

object TddCalculator : TreatmentsPlugin() {
    private val log = LoggerFactory.getLogger(L.DATATREATMENTS)

    fun calculate(days: Long): LongSparseArray<TDD> {
        val range = T.days(days + 1).msecs()
        val startTime = MidnightTime.calc(DateUtil.now()) - T.days(days).msecs()
        val endTime = MidnightTime.calc(DateUtil.now())
        initializeData(range)

        val result = LongSparseArray<TDD>()
        for (t in treatmentsFromHistory) {
            if (!t.isValid) continue
            if (t.date < startTime || t.date > endTime) continue
            val midnight = MidnightTime.calc(t.date)
            val tdd = result[midnight] ?: TDD(midnight, 0.0, 0.0, 0.0)
            tdd.bolus += t.insulin
            result.put(midnight, tdd)
        }

        for (t in startTime until endTime step T.mins(5).msecs()) {
            val midnight = MidnightTime.calc(t)
            val tdd = result[midnight] ?: TDD(midnight, 0.0, 0.0, 0.0)
            val tbr = getTempBasalFromHistory(t)
            val profile = ProfileFunctions.getInstance().getProfile(t) ?: continue
            val absoluteRate = tbr?.tempBasalConvertedToAbsolute(t, profile) ?: profile.getBasal(t)
            tdd.basal += absoluteRate / 60.0 * 5.0
            result.put(midnight, tdd)
        }
        for (i in 0 until result.size()) {
            val tdd = result.valueAt(i)
            tdd.total = tdd.bolus + tdd.basal
        }
        log.debug(result.toString())
        return result
    }

    fun averageTDD(tdds: LongSparseArray<TDD>): TDD {
        val totalTdd = TDD()
        for (i in 0 until tdds.size()) {
            val tdd = tdds.valueAt(i)
            totalTdd.basal += tdd.basal
            totalTdd.bolus += tdd.bolus
            totalTdd.total += tdd.total
        }
        totalTdd.basal /= tdds.size().toDouble()
        totalTdd.bolus /= tdds.size().toDouble()
        totalTdd.total /= tdds.size().toDouble()
        return totalTdd
    }

    fun stats(): Spanned {
        val tdds = calculate(7)
        val averageTdd = averageTDD(tdds)
        return HtmlHelper.fromHtml(
            "<b>" + MainApp.gs(R.string.tdd) + ":</b><br>" +
                toText(tdds) +
                "<b>" + MainApp.gs(R.string.average) + ":</b><br>" +
                averageTdd.toText(tdds.size())
        )
    }

    fun toText(tdds: LongSparseArray<TDD>): String {
        var t = ""
        for (i in 0 until tdds.size()) {
            t += "${tdds.valueAt(i).toText()}<br>"
        }
        return t
    }
}