package info.nightscout.workflow

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.core.events.EventIobCalculationProgress
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.graph.data.ScaledDataPoint
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class PrepareBasalDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    private var ctx: Context

    init {
        ctx = rh.getThemedCtx(context)
    }

    class PrepareBasalData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBasalData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_BASAL_DATA, 0, null))
        val baseBasalArray: MutableList<ScaledDataPoint> = ArrayList()
        val tempBasalArray: MutableList<ScaledDataPoint> = ArrayList()
        val basalLineArray: MutableList<ScaledDataPoint> = ArrayList()
        val absoluteBasalLineArray: MutableList<ScaledDataPoint> = ArrayList()
        var lastLineBasal = 0.0
        var lastAbsoluteLineBasal = -1.0
        var lastBaseBasal = 0.0
        var lastTempBasal = 0.0
        val endTime = data.overviewData.endTime
        val fromTime = data.overviewData.fromTime
        var time = fromTime
        while (time < endTime) {
            val progress = (time - fromTime).toDouble() / (endTime - fromTime) * 100.0
            rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_BASAL_DATA, progress.toInt(), null))
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 60 * 1000L
                continue
            }
            val basalData = data.iobCobCalculator.getBasalData(profile, time)
            val baseBasalValue = basalData.basal
            var absoluteLineValue = baseBasalValue
            var tempBasalValue = 0.0
            var basal = 0.0
            if (basalData.isTempBasalRunning) {
                tempBasalValue = basalData.tempBasalAbsolute
                absoluteLineValue = tempBasalValue
                if (tempBasalValue != lastTempBasal) {
                    tempBasalArray.add(ScaledDataPoint(time, lastTempBasal, data.overviewData.basalScale))
                    tempBasalArray.add(ScaledDataPoint(time, tempBasalValue.also { basal = it }, data.overviewData.basalScale))
                }
                if (lastBaseBasal != 0.0) {
                    baseBasalArray.add(ScaledDataPoint(time, lastBaseBasal, data.overviewData.basalScale))
                    baseBasalArray.add(ScaledDataPoint(time, 0.0, data.overviewData.basalScale))
                    lastBaseBasal = 0.0
                }
            } else {
                if (baseBasalValue != lastBaseBasal) {
                    baseBasalArray.add(ScaledDataPoint(time, lastBaseBasal, data.overviewData.basalScale))
                    baseBasalArray.add(ScaledDataPoint(time, baseBasalValue.also { basal = it }, data.overviewData.basalScale))
                    lastBaseBasal = baseBasalValue
                }
                if (lastTempBasal != 0.0) {
                    tempBasalArray.add(ScaledDataPoint(time, lastTempBasal, data.overviewData.basalScale))
                    tempBasalArray.add(ScaledDataPoint(time, 0.0, data.overviewData.basalScale))
                }
            }
            if (baseBasalValue != lastLineBasal) {
                basalLineArray.add(ScaledDataPoint(time, lastLineBasal, data.overviewData.basalScale))
                basalLineArray.add(ScaledDataPoint(time, baseBasalValue, data.overviewData.basalScale))
            }
            if (absoluteLineValue != lastAbsoluteLineBasal) {
                absoluteBasalLineArray.add(ScaledDataPoint(time, lastAbsoluteLineBasal, data.overviewData.basalScale))
                absoluteBasalLineArray.add(ScaledDataPoint(time, basal, data.overviewData.basalScale))
            }
            lastAbsoluteLineBasal = absoluteLineValue
            lastLineBasal = baseBasalValue
            lastTempBasal = tempBasalValue
            time += 60 * 1000L
        }

        // final points
        basalLineArray.add(ScaledDataPoint(endTime, lastLineBasal, data.overviewData.basalScale))
        baseBasalArray.add(ScaledDataPoint(endTime, lastBaseBasal, data.overviewData.basalScale))
        tempBasalArray.add(ScaledDataPoint(endTime, lastTempBasal, data.overviewData.basalScale))
        absoluteBasalLineArray.add(ScaledDataPoint(endTime, lastAbsoluteLineBasal, data.overviewData.basalScale))

        // create series
        data.overviewData.baseBasalGraphSeries = LineGraphSeries(Array(baseBasalArray.size) { i -> baseBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = rh.gac(ctx, info.nightscout.core.ui.R.attr.baseBasalColor )
            it.thickness = 0
        }
        data.overviewData.tempBasalGraphSeries = LineGraphSeries(Array(tempBasalArray.size) { i -> tempBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = rh.gac(ctx, info.nightscout.core.ui.R.attr.tempBasalColor )
            it.thickness = 0
        }
        data.overviewData.basalLineGraphSeries = LineGraphSeries(Array(basalLineArray.size) { i -> basalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = rh.getDisplayMetrics().scaledDensity * 2
                paint.pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
                paint.color = rh.gac(ctx, info.nightscout.core.ui.R.attr.basal )
            })
        }
        data.overviewData.absoluteBasalGraphSeries = LineGraphSeries(Array(absoluteBasalLineArray.size) { i -> absoluteBasalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { absolutePaint ->
                absolutePaint.style = Paint.Style.STROKE
                absolutePaint.strokeWidth = rh.getDisplayMetrics().scaledDensity * 2
                absolutePaint.color =rh.gac(ctx, info.nightscout.core.ui.R.attr.basal )
            })
        }
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_BASAL_DATA, 100, null))
        return Result.success()
    }
}