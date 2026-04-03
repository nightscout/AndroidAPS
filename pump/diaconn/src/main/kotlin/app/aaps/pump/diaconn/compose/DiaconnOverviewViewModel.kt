package app.aaps.pump.diaconn.compose

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
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
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
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.events.EventDiaconnG8NewStatus
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
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
import kotlin.math.min
import kotlin.math.roundToInt

sealed class DiaconnOverviewEvent {
    data object StartPairWizard : DiaconnOverviewEvent()
    data object StartHistory : DiaconnOverviewEvent()
    data object StartUserSettings : DiaconnOverviewEvent()
    data object ConfirmUnpair : DiaconnOverviewEvent()
}

@HiltViewModel
@Stable
class DiaconnOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    aapsSchedulers: AapsSchedulers,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val diaconnG8Pump: DiaconnG8Pump,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val uel: UserEntryLogger,
    private val preferences: Preferences,
    private val ch: ConcentrationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val disposable = CompositeDisposable()

    private val _events = MutableSharedFlow<DiaconnOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<DiaconnOverviewEvent> = _events

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, viewModelScope)

    private val rxTrigger = MutableStateFlow(0L)

    init {
        disposable += rxBus
            .toObservable(EventDiaconnG8NewStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ rxTrigger.value = System.currentTimeMillis() }, { aapsLogger.error(LTag.PUMP, "Error", it) })
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ rxTrigger.value = System.currentTimeMillis() }, { aapsLogger.error(LTag.PUMP, "Error", it) })

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
        diaconnG8Pump.lastConnectionFlow,
        diaconnG8Pump.systemRemainInsulinFlow,
        diaconnG8Pump.systemRemainBatteryFlow,
        diaconnG8Pump.lastBolusTimeFlow,
        diaconnG8Pump.lastBolusAmountFlow
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
        diaconnG8Pump.lastConnection = 0
        commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.clicked_connect_to_pump), null)
    }

    fun onHistoryClick() = _events.tryEmit(DiaconnOverviewEvent.StartHistory)
    fun onUserSettingsClick() = _events.tryEmit(DiaconnOverviewEvent.StartUserSettings)
    fun onPairClick() = _events.tryEmit(DiaconnOverviewEvent.StartPairWizard)
    fun onUnpairClick() = _events.tryEmit(DiaconnOverviewEvent.ConfirmUnpair)

    fun performUnpair() {
        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.DiaconnG8)
        preferences.remove(DiaconnStringNonKey.Address)
        preferences.remove(DiaconnStringNonKey.Name)
        rxBus.send(EventDiaconnG8DeviceChange())
        rxTrigger.value = System.currentTimeMillis()
    }

    private fun buildInitialState(): PumpOverviewUiState = buildUiState(
        lastConnectionTime = diaconnG8Pump.lastConnection,
        reservoir = diaconnG8Pump.systemRemainInsulin,
        battery = diaconnG8Pump.systemRemainBattery,
        lastBolusTime = diaconnG8Pump.lastBolusTime,
        lastBolusAmount = diaconnG8Pump.lastBolusAmount
    )

    private fun temporaryBasalToString(): String {
        val pump = diaconnG8Pump
        if (!pump.isTempBasalInProgress) return ""

        val passedMin = ((min(dateUtil.now(), pump.tempBasalStart + pump.tempBasalDuration) - pump.tempBasalStart) / 60.0 / 1000).roundToInt()
        return ch.basalRateString(PumpRate(pump.tempBasalAbsoluteRate), true) +
            "\n" + dateUtil.timeString(pump.tempBasalStart) +
            " " + passedMin + "/" + T.msecs(pump.tempBasalDuration).mins() + "'"
    }

    private fun extendedBolusToString(): String {
        val pump = diaconnG8Pump
        if (!pump.isExtendedInProgress) return ""
        //return "E "+ decimalFormatter.to2Decimal(extendedBolusDeliveredSoFar) +"/" + decimalFormatter.to2Decimal(extendedBolusAbsoluteRate) + "U/h @" +
        //     " " + extendedBolusPassedMinutes + "/" + extendedBolusMinutes + "'"
        return "E " + ch.basalRateString(PumpRate(pump.extendedBolusAbsoluteRate), true) +
            dateUtil.timeString(pump.extendedBolusStart) +
            " " + pump.extendedBolusPassedMinutes + "/" + pump.extendedBolusDurationInMinutes + "'"
    }

    private fun buildUiState(
        lastConnectionTime: Long,
        reservoir: Double,
        battery: Int?,
        lastBolusTime: Long?,
        lastBolusAmount: Double?
    ): PumpOverviewUiState {
        val pump = diaconnG8Pump
        val activePump = activePlugin.activePump

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
                ch.insulinAmountAgoString(PumpInsulin(lastBolusAmount), dateUtil.sinceString(lastBolusTime, rh))
            } else null
        } else null

        // Daily units
        val todayInsulinAmount = ch.fromPump(PumpInsulin(pump.todayBaseAmount + pump.todaySnackAmount + pump.todayMealAmount))
        val todayInsulinLimitAmount = ch.fromPump(PumpInsulin((pump.maxBasal * 24) + pump.maxBolusePerDay)).toInt()

        // Base basal rate
        val baseBasalRate = ch.basalRateString(activePump.baseBasalRate, true)

        // Temp basal / Extended bolus
        val tempBasalText = temporaryBasalToString()
        val extendedBolusText = extendedBolusToString()

        // Battery
        val batteryText = battery?.let { "${it}%" }

        // Reservoir
        val reservoirText = ch.insulinAmountString(PumpInsulin(pump.systemRemainInsulin)) // "/ 307 U" removed

        // Warn levels
        val lastConnectionLevel = if (lastConnectionTime != 0L) {
            val agoMinutes = ((System.currentTimeMillis() - lastConnectionTime) / 60_000).toInt()
            when {
                agoMinutes >= 31 -> StatusLevel.CRITICAL
                agoMinutes >= 16 -> StatusLevel.WARNING
                else             -> StatusLevel.NORMAL
            }
        } else StatusLevel.UNSPECIFIED

        val batteryLevel = when {
            battery == null -> StatusLevel.UNSPECIFIED
            battery <= 26   -> StatusLevel.CRITICAL
            battery <= 51   -> StatusLevel.WARNING
            else            -> StatusLevel.NORMAL
        }

        val reservoirLevel = when {
            ch.fromPump(PumpInsulin(pump.systemRemainInsulin)) <= 20.0 -> StatusLevel.CRITICAL
            ch.fromPump(PumpInsulin(pump.systemRemainInsulin)) <= 50.0 -> StatusLevel.WARNING
            else                              -> StatusLevel.NORMAL
        }

        val isConfigured = activePump.isConfigured()
        val isInitialized = activePump.isInitialized()

        // Info rows
        val infoRows = if (!isConfigured) emptyList() else buildList {
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.serial_number), value = pump.serialNo.toString()))

            batteryText?.let {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.battery_label), value = it, level = batteryLevel))
            }

            if (lastConnection.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_connection_label), value = lastConnection, level = lastConnectionLevel))
            }

            lastBolus?.let {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label), value = it))
            }

            add(
                PumpInfoRow(
                    label = rh.gs(app.aaps.core.ui.R.string.daily_units),
                    value = rh.gs(app.aaps.core.ui.R.string.reservoir_value, todayInsulinAmount, todayInsulinLimitAmount),
                    level = when {
                        todayInsulinAmount > todayInsulinLimitAmount * 0.9  -> StatusLevel.CRITICAL
                        todayInsulinAmount > todayInsulinLimitAmount * 0.75 -> StatusLevel.WARNING
                        else                                                -> StatusLevel.NORMAL
                    }
                )
            )

            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label), value = baseBasalRate))
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), value = tempBasalText, visible = tempBasalText.isNotEmpty()))
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.extended_bolus_label), value = extendedBolusText, visible = extendedBolusText.isNotEmpty()))
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.reservoir_label), value = reservoirText, level = reservoirLevel))
            add(PumpInfoRow(label = rh.gs(R.string.basal_step) + " / " + rh.gs(R.string.bolus_step), value = "${ch.fromPump(PumpInsulin(pump.basalStep))} / ${ch.fromPump(PumpInsulin(pump.bolusStep))}"))

            // Firmware
            val firmware = rh.gs(R.string.diaconn_g8_pump) +
                "\nVersion: ${pump.majorVersion}.${pump.minorVersion}" +
                "\nCountry: ${pump.country}" +
                "\nProductType: ${pump.productType}" +
                "\nManufacture: ${pump.makeYear}.${pump.makeMonth}.${pump.makeDay}"
            add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.firmware), value = firmware))
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

        val managementActions = buildList {
            add(
                PumpAction(
                    label = rh.gs(R.string.diaconng8_pump_settings),
                    icon = Icons.Filled.Settings,
                    category = ActionCategory.MANAGEMENT,
                    visible = isInitialized,
                    onClick = { onUserSettingsClick() }
                )
            )

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
                        label = rh.gs(app.aaps.core.ui.R.string.pump_pair),
                        icon = Icons.Filled.Bluetooth,
                        category = ActionCategory.MANAGEMENT,
                        onClick = { onPairClick() }
                    )
                )
            }
        }

        return PumpOverviewUiState(
            statusBanner = statusBanner,
            queueStatus = queueStatus,
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }
}
