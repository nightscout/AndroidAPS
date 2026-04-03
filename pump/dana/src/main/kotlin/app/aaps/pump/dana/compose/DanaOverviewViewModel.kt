package app.aaps.pump.dana.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaStringNonKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class DanaOverviewEvent {
    data object StartPairWizard : DanaOverviewEvent()
    data object StartHistory : DanaOverviewEvent()
    data object StartUserSettings : DanaOverviewEvent()
    data object ConfirmUnpair : DanaOverviewEvent()
}

@HiltViewModel
@Stable
open class DanaOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    protected val rh: ResourceHelper,
    rxBus: RxBus,
    aapsSchedulers: AapsSchedulers,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val danaPump: DanaPump,
    private val activePlugin: ActivePlugin,
    private val ch: ConcentrationHelper,
    private val persistenceLayer: PersistenceLayer,
    protected val uel: UserEntryLogger,
    protected val preferences: Preferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val disposable = CompositeDisposable()

    private val _events = MutableSharedFlow<DanaOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<DanaOverviewEvent> = _events

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, viewModelScope)

    // RxBus events converted to a flow trigger for recomposition
    protected val rxTrigger = MutableStateFlow(0L)

    init {
        disposable += rxBus
            .toObservable(EventDanaRNewStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ rxTrigger.value = System.currentTimeMillis() }, { aapsLogger.error(LTag.PUMP, "Error", it) })
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ rxTrigger.value = System.currentTimeMillis() }, { aapsLogger.error(LTag.PUMP, "Error", it) })

        // Observe EB/TB database changes for immediate UI updates
        persistenceLayer.observeChanges(EB::class.java)
            .onEach { rxTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)
        persistenceLayer.observeChanges(TB::class.java)
            .onEach { rxTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)
    }

    private data class PumpSnapshot(
        val lastConnectionTime: Long,
        val reservoir: Double,
        val battery: Int?,
        val lastBolusTime: Long?,
        val lastBolusAmount: Double?
    )

    private val pumpDataFlow = combine(
        danaPump.lastConnectionFlow,
        danaPump.reservoirRemainingUnitsFlow,
        danaPump.batteryRemainingFlow,
        danaPump.lastBolusTimeFlow,
        danaPump.lastBolusAmountFlow
    ) { lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount ->
        PumpSnapshot(lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount)
    }

    private val refreshFlow = combine(
        rxTrigger,
        communicationStatus.refreshTrigger,
        tickerFlow(60_000L)
    ) { _, _, _ -> }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        pumpDataFlow,
        refreshFlow
    ) { snapshot, _ ->
        buildUiState(snapshot.lastConnectionTime, snapshot.reservoir, snapshot.battery, snapshot.lastBolusTime, snapshot.lastBolusAmount)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildInitialState())

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    fun onRefreshClick() {
        aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
        danaPump.reset()
        commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.clicked_connect_to_pump), null)
    }

    fun onHistoryClick() {
        _events.tryEmit(DanaOverviewEvent.StartHistory)
    }

    fun onUserSettingsClick() {
        _events.tryEmit(DanaOverviewEvent.StartUserSettings)
    }

    fun onPairClick() {
        _events.tryEmit(DanaOverviewEvent.StartPairWizard)
    }

    fun onUnpairClick() {
        _events.tryEmit(DanaOverviewEvent.ConfirmUnpair)
    }

    /**
     * Override in subclasses for variant-specific unpair logic (e.g., BLE bond removal for DanaRS).
     * Default: clears stored device name, resets pump state, logs user entry.
     */
    open fun performUnpair() {
        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.Dana)
        preferences.remove(DanaStringNonKey.RName)
        danaPump.reset()
        rxTrigger.value = System.currentTimeMillis()
    }

    /**
     * Override in subclasses to add variant-specific management actions (e.g., BLE pair/unpair for DanaRS).
     */
    protected open fun buildManagementActions(pump: DanaPump, isInitialized: Boolean, isConfigured: Boolean): List<PumpAction> = buildList {
        // User Settings (only for non-Korean, non-legacy-DanaR firmware)
        if (pump.hwModel != 1 && pump.protocol != 0x00) {
            add(
                PumpAction(
                    label = rh.gs(R.string.danar_user_options),
                    icon = Icons.Filled.Settings,
                    category = ActionCategory.MANAGEMENT,
                    visible = isInitialized,
                    onClick = { onUserSettingsClick() }
                )
            )
        }

        // Pair / Unpair
        if (isConfigured) {
            add(
                PumpAction(
                    label = rh.gs(app.aaps.core.ui.R.string.pump_unpair),
                    icon = Icons.Filled.Bluetooth,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { onUnpairClick() }
                )
            )
        } else {
            add(
                PumpAction(
                    label = rh.gs(app.aaps.core.ui.R.string.pairing),
                    icon = Icons.Filled.Bluetooth,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { onPairClick() }
                )
            )
        }
    }

    private fun buildInitialState(): PumpOverviewUiState = buildUiState(
        lastConnectionTime = danaPump.lastConnection,
        reservoir = danaPump.reservoirRemainingUnits,
        battery = danaPump.batteryRemaining,
        lastBolusTime = danaPump.lastBolusTime,
        lastBolusAmount = danaPump.lastBolusAmount
    )

    private fun buildUiState(
        lastConnectionTime: Long,
        reservoir: Double,
        battery: Int?,
        lastBolusTime: Long?,
        lastBolusAmount: Double?
    ): PumpOverviewUiState {
        val pump = danaPump
        val activePump = activePlugin.activePump

        // Communication status (shared: pump status + queue)
        val statusBanner = communicationStatus.statusBanner()
        val queueStatus = communicationStatus.queueStatus()

        // Last connection
        val lastConnection = if (lastConnectionTime != 0L) {
            val agoMinutes = ((System.currentTimeMillis() - lastConnectionTime) / 60_000).toInt()
            dateUtil.timeString(lastConnectionTime) + " (" + rh.gs(app.aaps.core.interfaces.R.string.minago, agoMinutes) + ")"
        } else ""

        // Last bolus
        val lastBolus = if (lastBolusTime != null && lastBolusAmount != null) {
            val agoHours = (System.currentTimeMillis() - lastBolusTime).toDouble() / 3_600_000.0
            if (agoHours < 6.0) {
                ch.insulinAmountAgoString(
                    PumpInsulin(lastBolusAmount),
                    dateUtil.sinceString(lastBolusTime, rh)
                )
            } else null
        } else null

        // Base basal rate
        val baseBasalRate = "( ${pump.activeProfile + 1} )  " +
            rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, activePump.baseBasalRate.cU)

        // Temp basal
        val tempBasalText = pump.temporaryBasalToString()

        // Extended bolus
        val extendedBolusText = pump.extendedBolusToString()

        // Battery
        val batteryText = battery?.let { "${it}%" }

        // Reservoir
        val reservoirText = if (reservoir > 0.0)
            rh.gs(app.aaps.core.ui.R.string.reservoir_value, reservoir, 300)
        else null

        // Last connection warn level
        val lastConnectionLevel = if (lastConnectionTime != 0L) {
            val agoMinutes = ((System.currentTimeMillis() - lastConnectionTime) / 60_000).toInt()
            when {
                agoMinutes >= 31 -> StatusLevel.CRITICAL
                agoMinutes >= 16 -> StatusLevel.WARNING
                else             -> StatusLevel.NORMAL
            }
        } else StatusLevel.UNSPECIFIED

        // Battery warn level
        val batteryLevel = when {
            battery == null -> StatusLevel.UNSPECIFIED
            battery <= 26   -> StatusLevel.CRITICAL
            battery <= 51   -> StatusLevel.WARNING
            else            -> StatusLevel.NORMAL
        }

        // Reservoir warn level
        val reservoirLevel = when {
            reservoir <= 20.0 -> StatusLevel.CRITICAL
            reservoir <= 50.0 -> StatusLevel.WARNING
            else              -> StatusLevel.NORMAL
        }

        val isConfigured = activePump.isConfigured()
        val isInitialized = activePump.isInitialized()

        // Info rows
        val infoRows = if (!isConfigured) emptyList() else buildList {
            // 1. Serial number
            pump.serialNumber.takeIf { it.isNotEmpty() }?.let {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.serial_number), value = it))
            }

            // 2. Battery
            batteryText?.let {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.battery_label), value = it, level = batteryLevel))
            }

            // 3. Last connection
            if (lastConnection.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_connection_label), value = lastConnection, level = lastConnectionLevel))
            }

            // 4. Last bolus
            lastBolus?.let {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label), value = it))
            }

            // 5. Daily units
            add(
                PumpInfoRow(
                    label = rh.gs(app.aaps.core.ui.R.string.daily_units),
                    value = rh.gs(app.aaps.core.ui.R.string.reservoir_value, pump.dailyTotalUnits, pump.maxDailyTotalUnits),
                    level = when {
                        pump.dailyTotalUnits > pump.maxDailyTotalUnits * 0.9 -> StatusLevel.CRITICAL
                        pump.dailyTotalUnits > pump.maxDailyTotalUnits * 0.75 -> StatusLevel.WARNING
                        else -> StatusLevel.NORMAL
                    }
                )
            )

            // 6. Base basal rate
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label), value = baseBasalRate))

            // 7. Temp basal (hidden when empty)
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), value = tempBasalText, visible = tempBasalText.isNotEmpty()))

            // 8. Extended bolus (hidden when empty)
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.extended_bolus_label), value = extendedBolusText, visible = extendedBolusText.isNotEmpty()))

            // 9. Reservoir
            reservoirText?.let {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.reservoir_label), value = it, level = reservoirLevel))
            }

            // 10. Basal/bolus step
            add(PumpInfoRow(label = rh.gs(R.string.basal_bolus_step), value = "${pump.basalStep} / ${pump.bolusStep}"))

            // 11. Firmware
            if (pump.hwModel != 0) {
                add(
                    PumpInfoRow(
                        label = rh.gs(app.aaps.core.ui.R.string.firmware),
                        value = rh.gs(R.string.dana_model, pump.modelFriendlyName(), pump.hwModel, pump.protocol, pump.productCode)
                    )
                )
            }
        }

        // Actions
        val primaryActions = listOf(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                iconRes = app.aaps.core.ui.R.drawable.ic_refresh,
                category = ActionCategory.PRIMARY,
                visible = isInitialized,
                onClick = { onRefreshClick() }
            ),
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.pump_history),
                icon = Icons.AutoMirrored.Filled.List,
                category = ActionCategory.PRIMARY,
                visible = isInitialized,
                onClick = { onHistoryClick() }
            )
        )

        val managementActions = buildManagementActions(pump, isInitialized, isConfigured)

        return PumpOverviewUiState(
            statusBanner = statusBanner,
            queueStatus = queueStatus,
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }
}
