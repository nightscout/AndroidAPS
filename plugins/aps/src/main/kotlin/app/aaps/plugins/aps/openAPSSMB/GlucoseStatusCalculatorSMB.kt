package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSSMB.extensions.asRounded
import app.aaps.plugins.aps.openAPSSMB.extensions.log
import dagger.Reusable
import javax.inject.Inject

@Reusable
class GlucoseStatusCalculatorSMB @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val deltaCalculator: DeltaCalculator

) : GlucoseStatusProvider {

    override val glucoseStatusData: GlucoseStatus?
        get() = getGlucoseStatusData(false)

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatusSMB? {
        val data = iobCobCalculator.ads.getBucketedDataTableCopy() ?: return null

        val sizeRecords = data.size
        if (sizeRecords == 0) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==0")
            return null
        }
        if (data[0].timestamp < dateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
            aapsLogger.debug(LTag.GLUCOSE, "oldData")
            return null
        }
        val now = data[0]
        val nowDate = now.timestamp
        if (sizeRecords == 1) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==1")
            return GlucoseStatusSMB(
                glucose = now.recalculated,
                noise = 0.0,
                delta = 0.0,
                shortAvgDelta = 0.0,
                longAvgDelta = 0.0,
                date = nowDate,
            ).asRounded()
        }

        val deltaResult = deltaCalculator.calculateDeltas(data)

        return GlucoseStatusSMB(
            glucose = now.recalculated,
            date = nowDate,
            noise = 0.0, //for now set to nothing as not all CGMs report noise
            shortAvgDelta = deltaResult.shortAvgDelta,
            delta = deltaResult.delta,
            longAvgDelta = deltaResult.longAvgDelta,
        ).also { aapsLogger.debug(LTag.GLUCOSE, it.log(decimalFormatter)) }.asRounded()
    }

    companion object {
    }
}