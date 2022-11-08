package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.rawOrSmoothed
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.Translator
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class PrepareTreatmentsDataWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var translator: Translator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var sp: SP

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class PrepareTreatmentsData(
        val overviewData: OverviewData
    )

    override fun doWork(): Result {

        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as PrepareTreatmentsData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TREATMENTS_DATA, 0, null))
        data.overviewData.maxTreatmentsValue = 0.0
        data.overviewData.maxEpsValue = 0.0
        val filteredTreatments: MutableList<DataPointWithLabelInterface> = ArrayList()
        val filteredTherapyEvents: MutableList<DataPointWithLabelInterface> = ArrayList()
        val filteredEps: MutableList<DataPointWithLabelInterface> = ArrayList()

        repository.getBolusesDataFromTimeToTime(data.overviewData.fromTime, data.overviewData.endTime, true).blockingGet()
            .map { BolusDataPoint(it, rh, activePlugin, defaultValueHelper) }
            .filter { it.data.type == Bolus.Type.NORMAL || it.data.type == Bolus.Type.SMB }
            .forEach {
                it.y = getNearestBg(data.overviewData, it.x.toLong())
                filteredTreatments.add(it)
            }
        repository.getCarbsDataFromTimeToTimeExpanded(data.overviewData.fromTime, data.overviewData.endTime, true).blockingGet()
            .map { CarbsDataPoint(it, rh) }
            .forEach {
                it.y = getNearestBg(data.overviewData, it.x.toLong())
                filteredTreatments.add(it)
            }

        // ProfileSwitch
        repository.getEffectiveProfileSwitchDataFromTimeToTime(data.overviewData.fromTime, data.overviewData.endTime, true).blockingGet()
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
                    profileFunction,
                    translator
                )
            }
            .forEach(filteredTreatments::add)

        // Extended bolus
        if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            repository.getExtendedBolusDataFromTimeToTime(data.overviewData.fromTime, data.overviewData.endTime, true).blockingGet()
                .map { ExtendedBolusDataPoint(it, rh) }
                .filter { it.duration != 0L }
                .forEach {
                    it.y = getNearestBg(data.overviewData, it.x.toLong())
                    filteredTreatments.add(it)
                }
        }

        // Careportal
        repository.compatGetTherapyEventDataFromToTime(data.overviewData.fromTime - T.hours(6).msecs(), data.overviewData.endTime).blockingGet()
            .map { TherapyEventDataPoint(it, rh, profileFunction, translator) }
            .filterTimeframe(data.overviewData.fromTime, data.overviewData.endTime)
            .forEach {
                if (it.y == 0.0) it.y = getNearestBg(data.overviewData, it.x.toLong())
                filteredTherapyEvents.add(it)
            }

        // increase maxY if a treatment forces it's own height that's higher than a BG value
        filteredTreatments.map { it.y }
            .maxOrNull()
            ?.let(::addUpperChartMargin)
            ?.let { data.overviewData.maxTreatmentsValue = maxOf(data.overviewData.maxTreatmentsValue, it) }
        filteredTherapyEvents.map { it.y }
            .maxOrNull()
            ?.let(::addUpperChartMargin)
            ?.let { data.overviewData.maxTherapyEventValue = maxOf(data.overviewData.maxTherapyEventValue, it) }

        data.overviewData.treatmentsSeries = PointsWithLabelGraphSeries(filteredTreatments.toTypedArray())
        data.overviewData.therapyEventSeries = PointsWithLabelGraphSeries(filteredTherapyEvents.toTypedArray())
        data.overviewData.epsSeries = PointsWithLabelGraphSeries(filteredEps.toTypedArray())

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TREATMENTS_DATA, 100, null))
        return Result.success()
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileFunction.getUnits() == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4

    private fun getNearestBg(overviewData: OverviewData, date: Long): Double {
        overviewData.bgReadingsArray.let { bgReadingsArray ->
            for (reading in bgReadingsArray) {
                if (reading.timestamp > date) continue
                return Profile.fromMgdlToUnits(reading.rawOrSmoothed(sp), profileFunction.getUnits())
            }
            return if (bgReadingsArray.isNotEmpty()) Profile.fromMgdlToUnits(bgReadingsArray[0].rawOrSmoothed(sp), profileFunction.getUnits())
            else Profile.fromMgdlToUnits(100.0, profileFunction.getUnits())
        }
    }

    private fun <E : DataPointWithLabelInterface> List<E>.filterTimeframe(fromTime: Long, endTime: Long): List<E> =
        filter { it.x + it.duration >= fromTime && it.x <= endTime }
}