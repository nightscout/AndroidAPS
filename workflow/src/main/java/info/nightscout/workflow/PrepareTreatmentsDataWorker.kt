package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.main.events.EventIobCalculationProgress
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.graph.data.BolusDataPoint
import app.aaps.core.main.graph.data.CarbsDataPoint
import app.aaps.core.main.graph.data.DataPointWithLabelInterface
import app.aaps.core.main.graph.data.EffectiveProfileSwitchDataPoint
import app.aaps.core.main.graph.data.ExtendedBolusDataPoint
import app.aaps.core.main.graph.data.HeartRateDataPoint
import app.aaps.core.main.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.main.graph.data.TherapyEventDataPoint
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.main.workflow.CalculationWorkflow
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.TherapyEvent
import info.nightscout.database.impl.AppRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class PrepareTreatmentsDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var translator: Translator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var decimalFormatter: DecimalFormatter

    class PrepareTreatmentsData(
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareTreatmentsData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val endTime = data.overviewData.endTime
        val fromTime = data.overviewData.fromTime
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TREATMENTS_DATA, 0, null))
        data.overviewData.maxTreatmentsValue = 0.0
        data.overviewData.maxEpsValue = 0.0
        val filteredTreatments: MutableList<DataPointWithLabelInterface> = ArrayList()
        val filteredTherapyEvents: MutableList<DataPointWithLabelInterface> = ArrayList()
        val filteredEps: MutableList<DataPointWithLabelInterface> = ArrayList()

        repository.getBolusesDataFromTimeToTime(fromTime, endTime, true).blockingGet()
            .map { BolusDataPoint(it, rh, activePlugin, defaultValueHelper, decimalFormatter) }
            .filter { it.data.type == Bolus.Type.NORMAL || it.data.type == Bolus.Type.SMB }
            .forEach {
                it.y = getNearestBg(data.overviewData, it.x.toLong())
                filteredTreatments.add(it)
            }
        repository.getCarbsDataFromTimeToTimeExpanded(fromTime, endTime, true).blockingGet()
            .map { CarbsDataPoint(it, rh) }
            .forEach {
                it.y = getNearestBg(data.overviewData, it.x.toLong())
                filteredTreatments.add(it)
            }

        // ProfileSwitch
        repository.getEffectiveProfileSwitchDataFromTimeToTime(fromTime, endTime, true).blockingGet()
            .map { EffectiveProfileSwitchDataPoint(it, rh, data.overviewData.epsScale) }
            .forEach {
                data.overviewData.maxEpsValue = maxOf(data.overviewData.maxEpsValue, it.data.originalPercentage.toDouble())
                filteredEps.add(it)
            }

        // OfflineEvent
        repository.getOfflineEventDataFromTimeToTime(data.overviewData.fromTime, data.overviewData.endTime, true).blockingGet()
            .map {
                TherapyEventDataPoint(
                    TherapyEvent(timestamp = it.timestamp, duration = it.duration, type = TherapyEvent.Type.APS_OFFLINE, glucoseUnit = TherapyEvent.GlucoseUnit.MMOL),
                    rh,
                    profileUtil,
                    translator
                )
            }
            .forEach(filteredTreatments::add)

        // Extended bolus
        if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            repository.getExtendedBolusDataFromTimeToTime(fromTime, endTime, true).blockingGet()
                .map { ExtendedBolusDataPoint(it, rh, decimalFormatter) }
                .filter { it.duration != 0L }
                .forEach {
                    it.y = getNearestBg(data.overviewData, it.x.toLong())
                    filteredTreatments.add(it)
                }
        }

        // Careportal
        repository.compatGetTherapyEventDataFromToTime(fromTime - T.hours(6).msecs(), endTime).blockingGet()
            .map { TherapyEventDataPoint(it, rh, profileUtil, translator) }
            .filterTimeframe(fromTime, endTime)
            .forEach {
                if (it.y == 0.0) it.y = getNearestBg(data.overviewData, it.x.toLong())
                filteredTherapyEvents.add(it)
            }

        // increase maxY if a treatment forces it's own height that's higher than a BG value
        filteredTreatments.maxOfOrNull { it.y }
            ?.let(::addUpperChartMargin)
            ?.let { data.overviewData.maxTreatmentsValue = maxOf(data.overviewData.maxTreatmentsValue, it) }
        filteredTherapyEvents.maxOfOrNull { it.y }
            ?.let(::addUpperChartMargin)
            ?.let { data.overviewData.maxTherapyEventValue = maxOf(data.overviewData.maxTherapyEventValue, it) }

        data.overviewData.treatmentsSeries = PointsWithLabelGraphSeries(filteredTreatments.toTypedArray())
        data.overviewData.therapyEventSeries = PointsWithLabelGraphSeries(filteredTherapyEvents.toTypedArray())
        data.overviewData.epsSeries = PointsWithLabelGraphSeries(filteredEps.toTypedArray())

        data.overviewData.heartRateGraphSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>(
            repository.getHeartRatesFromTimeToTime(fromTime, endTime)
                .map { hr -> HeartRateDataPoint(hr, rh) }
                .toTypedArray()).apply { color = rh.gac(null, app.aaps.core.ui.R.attr.heartRateColor) }

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TREATMENTS_DATA, 100, null))
        return Result.success()
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileUtil.units == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4

    private fun getNearestBg(overviewData: OverviewData, date: Long): Double {
        overviewData.bgReadingsArray.let { bgReadingsArray ->
            for (reading in bgReadingsArray) {
                if (reading.timestamp > date) continue
                return profileUtil.fromMgdlToUnits(reading.value)
            }
            return if (bgReadingsArray.isNotEmpty()) profileUtil.fromMgdlToUnits(bgReadingsArray[0].value)
            else profileUtil.fromMgdlToUnits(100.0)
        }
    }

    private fun <E : DataPointWithLabelInterface> List<E>.filterTimeframe(fromTime: Long, endTime: Long): List<E> =
        filter { it.x + it.duration >= fromTime && it.x <= endTime }
}
