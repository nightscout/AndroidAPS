package info.nightscout.androidaps.utils.stats

import android.text.Spanned
import android.util.LongSparseArray
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.TDD
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.treatments.TreatmentService
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class TddCalculator @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    val mainApp: MainApp,
    val sp: SP,
    val activePlugin: ActivePluginProvider,
    val profileFunction: ProfileFunction,
    fabricPrivacy: FabricPrivacy,
    nsUpload: NSUpload,
    private val dateUtil: DateUtil,
    uploadQueue: UploadQueue
) : TreatmentsPlugin(injector, aapsLogger, rxBus, resourceHelper, mainApp, sp, profileFunction, activePlugin, nsUpload, fabricPrivacy, dateUtil, uploadQueue) {

    init {
        service = TreatmentService(injector) // plugin is not started
    }

    fun calculate(days: Long): LongSparseArray<TDD> {
        val range = T.days(days + 1).msecs()
        val startTime = MidnightTime.calc(DateUtil.now() - T.days(days).msecs())
        val endTime = MidnightTime.calc(DateUtil.now())
        initializeData(range)

        val result = LongSparseArray<TDD>()
        for (t in treatmentsFromHistory) {
            if (!t.isValid) continue
            if (t.date < startTime || t.date > endTime) continue
            val midnight = MidnightTime.calc(t.date)
            val tdd = result[midnight] ?: TDD(midnight, 0.0, 0.0, 0.0)
            tdd.bolus += t.insulin
            tdd.carbs += t.carbs
            result.put(midnight, tdd)
        }

        for (t in startTime until endTime step T.mins(5).msecs()) {
            val midnight = MidnightTime.calc(t)
            val tdd = result[midnight] ?: TDD(midnight, 0.0, 0.0, 0.0)
            val tbr = getTempBasalFromHistory(t)
            val profile = profileFunction.getProfile(t, this) ?: continue
            val absoluteRate = tbr?.tempBasalConvertedToAbsolute(t, profile) ?: profile.getBasal(t)
            tdd.basal += absoluteRate / 60.0 * 5.0

            if (!activePlugin.getActivePump().isFakingTempsByExtendedBoluses()) {
                // they are not included in TBRs
                val eb = getExtendedBolusFromHistory(t)
                val absoluteEbRate = eb?.absoluteRate() ?: 0.0
                tdd.bolus += absoluteEbRate / 60.0 * 5.0
            }
            result.put(midnight, tdd)
        }
        for (i in 0 until result.size()) {
            val tdd = result.valueAt(i)
            tdd.total = tdd.bolus + tdd.basal
        }
        aapsLogger.debug(LTag.CORE, result.toString())
        return result
    }

    private fun averageTDD(tdds: LongSparseArray<TDD>): TDD {
        val totalTdd = TDD()
        for (i in 0 until tdds.size()) {
            val tdd = tdds.valueAt(i)
            totalTdd.basal += tdd.basal
            totalTdd.bolus += tdd.bolus
            totalTdd.total += tdd.total
            totalTdd.carbs += tdd.carbs
        }
        totalTdd.basal /= tdds.size().toDouble()
        totalTdd.bolus /= tdds.size().toDouble()
        totalTdd.total /= tdds.size().toDouble()
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

    private fun toText(tdds: LongSparseArray<TDD>, includeCarbs: Boolean): String {
        var t = ""
        for (i in 0 until tdds.size()) {
            t += "${tdds.valueAt(i).toText(resourceHelper, dateUtil, includeCarbs)}<br>"
        }
        return t
    }
}