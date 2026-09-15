package app.aaps.ui.compose.testing

import app.aaps.core.interfaces.overview.graph.AapsClientStatusData
import app.aaps.core.interfaces.overview.graph.AbsIobGraphData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BasalGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgiGraphData
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.DevSlopeGraphData
import app.aaps.core.interfaces.overview.graph.DeviationsGraphData
import app.aaps.core.interfaces.overview.graph.EpsGraphPoint
import app.aaps.core.interfaces.overview.graph.HeartRateGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.ProfileDisplayData
import app.aaps.core.interfaces.overview.graph.RatioGraphData
import app.aaps.core.interfaces.overview.graph.RunningModeDisplayData
import app.aaps.core.interfaces.overview.graph.RunningModeGraphData
import app.aaps.core.interfaces.overview.graph.StepsGraphData
import app.aaps.core.interfaces.overview.graph.TargetLineData
import app.aaps.core.interfaces.overview.graph.TbrDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.overview.graph.VarSensGraphData
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An empty, in-memory [OverviewDataCache].
 *
 * A mock is the wrong tool here: the overview view models read about twenty flows off this
 * interface at construction time, and an unstubbed one answers `null`, which surfaces as an NPE
 * deep inside a `combine` far from the missing stub. A real object with real
 * `MutableStateFlow`s cannot have a gap, and a test can simply write to the flow it cares about.
 */
class FakeOverviewDataCache : OverviewDataCache {

    override val timeRangeFlow = MutableStateFlow<TimeRange?>(null)
    override val calcProgressFlow = MutableStateFlow(0)

    override val bgReadingsFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    override val bucketedDataFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    override val predictionsFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    override val bgInfoFlow = MutableStateFlow<BgInfoData?>(null)

    override val tempTargetFlow = MutableStateFlow<TempTargetDisplayData?>(null)
    override val profileFlow = MutableStateFlow<ProfileDisplayData?>(null)
    override val runningModeFlow = MutableStateFlow<RunningModeDisplayData?>(null)
    override val tbrFlow = MutableStateFlow<TbrDisplayData?>(null)

    override val iobGraphFlow = MutableStateFlow(IobGraphData(emptyList(), emptyList()))
    override val absIobGraphFlow = MutableStateFlow(AbsIobGraphData(emptyList()))
    override val cobGraphFlow = MutableStateFlow(CobGraphData(emptyList(), emptyList()))
    override val activityGraphFlow = MutableStateFlow(ActivityGraphData(emptyList(), emptyList()))
    override val bgiGraphFlow = MutableStateFlow(BgiGraphData(emptyList(), emptyList()))
    override val deviationsGraphFlow = MutableStateFlow(DeviationsGraphData(emptyList()))
    override val ratioGraphFlow = MutableStateFlow(RatioGraphData(emptyList()))
    override val devSlopeGraphFlow = MutableStateFlow(DevSlopeGraphData(emptyList(), emptyList()))
    override val varSensGraphFlow = MutableStateFlow(VarSensGraphData(emptyList()))
    override val heartRateGraphFlow = MutableStateFlow(HeartRateGraphData(emptyList()))
    override val stepsGraphFlow = MutableStateFlow(StepsGraphData(emptyList()))

    override val treatmentGraphFlow = MutableStateFlow(TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList()))
    override val epsGraphFlow = MutableStateFlow<List<EpsGraphPoint>>(emptyList())
    override val basalGraphFlow = MutableStateFlow(BasalGraphData(emptyList(), emptyList(), 0.0))
    override val targetLineFlow = MutableStateFlow(TargetLineData(emptyList()))
    override val runningModeGraphFlow = MutableStateFlow(RunningModeGraphData(emptyList()))

    override val nsClientStatusFlow = MutableStateFlow(AapsClientStatusData())

    override fun updateTimeRange(range: TimeRange?) { timeRangeFlow.value = range }
    override fun updateBgReadings(data: List<BgDataPoint>) { bgReadingsFlow.value = data }
    override fun updateBucketedData(data: List<BgDataPoint>) { bucketedDataFlow.value = data }
    override fun updatePredictions(data: List<BgDataPoint>) { predictionsFlow.value = data }
    override fun updateBgInfo(data: BgInfoData?) { bgInfoFlow.value = data }

    override fun refreshTempTarget() = Unit
    override fun refreshProfile() = Unit
    override fun refreshRunningMode() = Unit
    override fun refreshTbr() = Unit

    override fun updateIobGraph(data: IobGraphData) { iobGraphFlow.value = data }
    override fun updateAbsIobGraph(data: AbsIobGraphData) { absIobGraphFlow.value = data }
    override fun updateCobGraph(data: CobGraphData) { cobGraphFlow.value = data }
    override fun updateActivityGraph(data: ActivityGraphData) { activityGraphFlow.value = data }
    override fun updateBgiGraph(data: BgiGraphData) { bgiGraphFlow.value = data }
    override fun updateDeviationsGraph(data: DeviationsGraphData) { deviationsGraphFlow.value = data }
    override fun updateRatioGraph(data: RatioGraphData) { ratioGraphFlow.value = data }
    override fun updateDevSlopeGraph(data: DevSlopeGraphData) { devSlopeGraphFlow.value = data }
    override fun updateVarSensGraph(data: VarSensGraphData) { varSensGraphFlow.value = data }
    override fun updateHeartRateGraph(data: HeartRateGraphData) { heartRateGraphFlow.value = data }
    override fun updateStepsGraph(data: StepsGraphData) { stepsGraphFlow.value = data }

    override fun reset() = Unit
}
