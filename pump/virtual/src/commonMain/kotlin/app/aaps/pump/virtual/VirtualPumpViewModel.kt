package app.aaps.pump.virtual

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpTempBasalType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.baseBasalRange
import app.aaps.core.interfaces.pump.defs.hasExtendedBasals
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewStateBuilder
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.virtual.keys.VirtualBooleanNonPreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class VirtualPumpViewModel(
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val rh: TextResolver,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val ch: ConcentrationHelper,
    rxBus: RxBus,
    commandQueue: CommandQueue,
    scope: CoroutineScope
) {

    private val stateBuilder = PumpOverviewStateBuilder(rh)
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, scope)

    // VirtualPump has no hardware — it relies on DB to know active TB/EB
    private val dbChanged = merge(
        persistenceLayer.observeChanges<TB>().map { },
        persistenceLayer.observeChanges<EB>().map { },
        persistenceLayer.observeChanges<EPS>().map { }
    ).onStart { emit(Unit) }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        preferences.observe(VirtualBooleanNonPreferenceKey.IsSuspended),
        virtualPumpPlugin.pumpTypeFlow,
        virtualPumpPlugin.batteryPercentFlow,
        virtualPumpPlugin.reservoirInUnitsFlow,
        dbChanged,
        communicationStatus.refreshTrigger,
        tickerFlow()
    ) { values ->
        val isSuspended = values[0] as Boolean
        val pumpType = values[1] as PumpType?
        // @Suppress("UNUSED_VARIABLE") val battery = values[2] as Int
        // @Suppress("UNUSED_VARIABLE") val reservoir = values[3] as Int
        // values[4] = dbChanged (Unit), values[5] = ticker (Unit)
        buildUiState(isSuspended, pumpType)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), buildInitialState())

    private fun onSuspendToggle(suspend: Boolean) {
        preferences.put(VirtualBooleanNonPreferenceKey.IsSuspended, suspend)
    }

    private fun buildInitialState(): PumpOverviewUiState {
        val isSuspended = preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)
        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            queueStatus = communicationStatus.queueStatus(),
            infoRows = emptyList(),
            managementActions = listOf(
                PumpAction(
                    label = rh.gs(CoreUiStrings.pump_suspend),
                    icon = IcLoopPaused,
                    category = ActionCategory.MANAGEMENT,
                    visible = !isSuspended,
                    onClick = { onSuspendToggle(true) }
                ),
                PumpAction(
                    label = rh.gs(CoreUiStrings.pump_resume),
                    icon = Icons.Filled.PlayArrow,
                    category = ActionCategory.MANAGEMENT,
                    visible = isSuspended,
                    onClick = { onSuspendToggle(false) }
                )
            )
        )
    }

    private suspend fun buildUiState(isSuspended: Boolean, pumpType: PumpType?): PumpOverviewUiState {
        virtualPumpPlugin.refreshConfiguration()
        val profile = pumpSync.expectedPumpState().profile
        val now = dateUtil.now()

        val tempBasalText = profile?.let {
            persistenceLayer.getTemporaryBasalActiveAt(now)
                ?.let { tempBasal ->
                    ch.basalTbrString(
                        rate = PumpRate(tempBasal.rate),
                        startTime = tempBasal.timestamp,
                        durationInMin = T.msecs(tempBasal.duration).mins().toInt(),
                        isAbsolute = tempBasal.isAbsolute
                    )
                }
        } ?: ""

        val extendedBolusText = persistenceLayer.getExtendedBolusActiveAt(now)
            ?.let {
                ch.basalTbrString(
                    rate = PumpRate(it.rate),
                    startTime = it.timestamp,
                    durationInMin = T.msecs(it.duration).mins().toInt(),
                    isExtended = true
                )
            } ?: ""

        // Format values for the shared builder
        val lastConnection = virtualPumpPlugin.lastDataTime.value.takeIf { it != 0L }
            ?.let { dateUtil.minAgo(rh, it) } ?: ""

        val lastBolus = virtualPumpPlugin.lastBolusAmount.value?.let { amount ->
            virtualPumpPlugin.lastBolusTime.value?.takeIf { it != 0L }?.let { time ->
                ch.insulinAmountAgoString(amount, time)
            }
        }

        val baseBasalRate = profile?.getBasal()?.let { rate ->
            ch.basalRateString(PumpRate(rate), isAbsolute = true)
        }

        val battery = virtualPumpPlugin.batteryLevel.value?.let { level ->
            rh.gs(CoreUiStrings.format_percent, level)
        }

        val reservoir = ch.insulinAmountString(virtualPumpPlugin.reservoirLevel.value)

        // Common rows from shared builder
        val commonRows = stateBuilder.buildCommonRows(
            lastConnection = lastConnection,
            lastBolus = lastBolus,
            baseBasalRate = baseBasalRate,
            tempBasalText = tempBasalText,
            extendedBolusText = extendedBolusText,
            battery = battery,
            reservoir = reservoir,
            serialNumber = virtualPumpPlugin.serialNumber()
        )

        // Pump-specific rows
        val specificRows = buildList {
            pumpType?.let { pt ->
                add(
                    PumpInfoRow(
                        label = rh.gs(VirtualStrings.virtual_pump_type),
                        value = pt.description
                    )
                )
                add(
                    PumpInfoRow(
                        label = rh.gs(VirtualStrings.virtual_pump_definition),
                        value = pt.getFullDescription(rh)
                    )
                )
            }
        }

        val managementActions = listOf(
            PumpAction(
                label = rh.gs(CoreUiStrings.pump_suspend),
                icon = IcLoopPaused,
                category = ActionCategory.MANAGEMENT,
                visible = !isSuspended,
                onClick = { onSuspendToggle(true) }
            ),
            PumpAction(
                label = rh.gs(CoreUiStrings.pump_resume),
                icon = Icons.Filled.PlayArrow,
                category = ActionCategory.MANAGEMENT,
                visible = isSuspended,
                onClick = { onSuspendToggle(false) }
            )
        )

        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            queueStatus = communicationStatus.queueStatus(),
            infoRows = commonRows + specificRows,
            managementActions = managementActions
        )
    }

    private fun PumpType.getFullDescription(rh: TextResolver): String {
        val unit = if (pumpTempBasalType() == PumpTempBasalType.Percent) "%" else ""
        val eb = extendedBolusSettings() ?: return ""
        val tbr = tbrSettings() ?: return ""
        val extendedNote = if (hasExtendedBasals()) rh.gs(VirtualStrings.def_extended_note) else ""
        return rh.gs(
            VirtualStrings.virtual_pump_pump_def,
            bolusSize().toString(),
            eb.step, eb.durationStep, eb.maxDuration / 60,
            baseBasalRange(),
            tbr.minDose.toString() + unit + "-" + tbr.maxDose + unit, tbr.step.toString() + unit,
            tbr.durationStep, tbr.maxDuration / 60, extendedNote
        )
    }
}
