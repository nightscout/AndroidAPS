package app.aaps.pump.ap550s.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.ap550s.AP550Pump
import app.aaps.pump.ap550s.AP550PumpPlugin
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.events.EventAP550NewStatus
import app.aaps.pump.ap550s.events.EventPumpSuspendedChange
import app.aaps.pump.ap550s.keys.Ap550StringNonKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

/** One-time requests the overview screen sends to its host (navigation and dialogs). */
sealed class Ap550OverviewEvent {
    data object StartPairWizard : Ap550OverviewEvent()
    data object StartHistory : Ap550OverviewEvent()
    data object StartConnectStatus : Ap550OverviewEvent()
    data object StartUserSettings : Ap550OverviewEvent()
    data object ConfirmSuspendResume : Ap550OverviewEvent()
    data object ConfirmMute : Ap550OverviewEvent()
    data object ShowReconnectDialog : Ap550OverviewEvent()
}

/**
 * Connection state drawn on the pump card at the top of the overview.
 *
 * The order of the states matters: a pump that is connected but not yet authenticated is shown as
 * disconnected, because nothing can be delivered until the pairing handshake finished.
 */
enum class Ap550ConnectionPhase {
    UNBOUND,
    CONNECTING,
    CONNECTED_NOT_PAIRED,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED
}

/** Pump card state: which icon to draw, what it says, and which buttons apply. */
@Immutable
data class Ap550ConnectionUiState(
    val phase: Ap550ConnectionPhase = Ap550ConnectionPhase.DISCONNECTED,
    val statusText: String = "",
    val description: String = "",
    val pumpSuspended: Boolean = false,
    val pumpMuted: Boolean = false
)

/**
 * Overview screen for the AP550 driver.
 *
 * The shared [PumpOverviewUiState] carries the info rows and the buttons. The pump card at the top
 * is pump specific, so it comes from [connectionState] and is drawn through the shared screen's
 * `customContent` slot.
 *
 * Everything is recomputed from the pump object and the plugin when one of the flows fires: pump
 * state changes, communication status, the command queue or the one minute ticker. The relative
 * time texts ("5m ago") need that ticker to stay honest.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class Ap550OverviewViewModel(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val ch: ConcentrationHelper,
    private val ap550Pump: AP550Pump,
    private val ap550PumpPlugin: AP550PumpPlugin,
    private val preferences: Preferences
) : ViewModel() {

    private val _events = MutableSharedFlow<Ap550OverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<Ap550OverviewEvent> = _events

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, viewModelScope)

    /** Bumped by every event that can change what the screen shows. */
    private val refreshTrigger = MutableStateFlow(0L)

    /**
     * Last connection status announced over the bus. The driver cannot answer `isConnecting()`
     * while it dials: Android only reports connected and disconnected in the GATT callback, so
     * `STATE_CONNECTING` is never recorded. The command queue fills that gap - it sends a
     * CONNECTING event every second while it waits for the pump. Following that event, like the
     * old fragment did, is the only way the card can show "connecting".
     */
    @Volatile
    private var lastConnectionStatus = EventPumpStatusChanged.Status.DISCONNECTED

    init {
        // viewModelScope dies with the view model, like the CompositeDisposable it replaces. The
        // bodies only write a timestamp, so the dispatcher does not matter. UNDISPATCHED because
        // RxBus has no replay, so a scheduled collector could miss an event sent before it starts.
        rxBus.toFlow(EventAP550NewStatus::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { refreshTrigger.value = System.currentTimeMillis() }
        rxBus.toFlow(EventInitializationChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { refreshTrigger.value = System.currentTimeMillis() }
        // The pump reports its own suspend state change, which is not part of the status event.
        rxBus.toFlow(EventPumpSuspendedChange::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { refreshTrigger.value = System.currentTimeMillis() }

        // Temp basal and extended bolus are written straight to the database by the driver, so the
        // rows have to follow the database, not an RxBus event.
        persistenceLayer.observeChanges(EB::class)
            .onEach { refreshTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)
        persistenceLayer.observeChanges(TB::class)
            .onEach { refreshTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)

        rxBus.toFlow(EventPumpStatusChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                refreshTrigger.value = System.currentTimeMillis()
                // Plain progress messages ("getting pump status" and so on) ride on PERFORMING and
                // must not end a connecting phase that is still running.
                if (event.status != EventPumpStatusChanged.Status.PERFORMING) {
                    lastConnectionStatus = event.status
                }
                // While a paired pump is being connected, remind the user every two minutes that the
                // pump did not answer, instead of letting the spinner run without explanation.
                if (event.status == EventPumpStatusChanged.Status.CONNECTING &&
                    event.secondsElapsed != 0 &&
                    event.secondsElapsed % RECONNECT_PROMPT_SEC == 0 &&
                    preferences.get(Ap550StringNonKey.SerialNumber).isNotEmpty()
                ) {
                    _events.tryEmit(Ap550OverviewEvent.ShowReconnectDialog)
                }
            }

        // The pairing screen writes these preferences. The overview has to follow right away, without
        // waiting for the next tick, or the card would still say "pair the pump" after a pairing.
        preferences.observe(Ap550StringNonKey.SerialNumber)
            .drop(1)
            .onEach { refreshTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)
    }

    private data class PumpSnapshot(
        val lastConnectionTime: Long,
        val reservoir: Double,
        val battery: Int?,
        val lastBolusTime: Long?,
        val lastBolusAmount: Double?
    )

    private data class ScreenState(
        val overview: PumpOverviewUiState,
        val connection: Ap550ConnectionUiState
    )

    private val pumpDataFlow = combine(
        ap550Pump.lastConnectionFlow,
        ap550Pump.reservoirRemainingUnitsFlow,
        ap550Pump.batteryRemainingFlow,
        ap550Pump.lastBolusTimeFlow,
        ap550Pump.lastBolusAmountFlow
    ) { lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount ->
        PumpSnapshot(lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount)
    }

    private val screenState: StateFlow<ScreenState> = combine(
        pumpDataFlow,
        refreshTrigger,
        communicationStatus.refreshTrigger,
        tickerFlow(T.mins(1).msecs())
    ) { snapshot, _, _, _ ->
        ScreenState(overview = buildOverview(snapshot), connection = buildConnection())
    }.flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ScreenState(overview = buildOverview(currentSnapshot()), connection = buildConnection())
        )

    val uiState: StateFlow<PumpOverviewUiState> = screenState
        .map { it.overview }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), screenState.value.overview)

    val connectionState: StateFlow<Ap550ConnectionUiState> = screenState
        .map { it.connection }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), screenState.value.connection)

    private fun currentSnapshot() = PumpSnapshot(
        lastConnectionTime = ap550Pump.lastConnection,
        reservoir = ap550Pump.reservoirRemainingUnits,
        battery = ap550Pump.batteryRemaining,
        lastBolusTime = ap550Pump.lastBolusTimeFlow.value,
        lastBolusAmount = ap550Pump.lastBolusAmountFlow.value
    )

    /** True when a pump was paired. The pairing screen stores the serial number it read. */
    private fun isPumpBound(): Boolean = preferences.get(Ap550StringNonKey.SerialNumber).isNotEmpty()

    /**
     * Connection card. Same order as the old screen: connecting wins, then connected, then
     * disconnecting, and everything else counts as disconnected.
     */
    private fun buildConnection(): Ap550ConnectionUiState {
        // `isConnecting()` cannot see the dial (see lastConnectionStatus), so the CONNECTING and
        // HANDSHAKING events from the command queue decide when the card shows "connecting". They
        // stop when the queue gives up and sends DISCONNECTED. A pump that is connected always
        // wins: the last status can still say CONNECTING when the queue kept counting seconds
        // while the last part of the connect attempt ran.
        val connecting = !ap550PumpPlugin.isConnected() &&
            (ap550PumpPlugin.isConnecting() ||
                lastConnectionStatus == EventPumpStatusChanged.Status.CONNECTING ||
                lastConnectionStatus == EventPumpStatusChanged.Status.HANDSHAKING)
        return when {
            !isPumpBound()          ->
                Ap550ConnectionUiState(
                    phase = Ap550ConnectionPhase.UNBOUND,
                    statusText = rh.gs(R.string.ap550_status_unbound),
                    description = rh.gs(R.string.ap550_bind_uis_pump)
                )

            connecting              ->
                Ap550ConnectionUiState(
                    phase = Ap550ConnectionPhase.CONNECTING,
                    statusText = rh.gs(R.string.ap550_status_connecting)
                )

            ap550PumpPlugin.isConnected() && !ap550Pump.paired ->
                Ap550ConnectionUiState(
                    phase = Ap550ConnectionPhase.CONNECTED_NOT_PAIRED,
                    statusText = rh.gs(R.string.ap550_status_disconnected),
                    description = rh.gs(R.string.ap550_tap_to_reconnect)
                )

            ap550PumpPlugin.isConnected() ->
                Ap550ConnectionUiState(
                    phase = Ap550ConnectionPhase.CONNECTED,
                    statusText = rh.gs(R.string.ap550s_connected),
                    pumpSuspended = ap550Pump.pumpSuspended,
                    pumpMuted = ap550Pump.pumpMuteStatus
                )

            ap550PumpPlugin.disconnecting() ->
                Ap550ConnectionUiState(
                    phase = Ap550ConnectionPhase.DISCONNECTING,
                    statusText = rh.gs(R.string.ap550_status_disconnecting)
                )

            else            ->
                Ap550ConnectionUiState(
                    phase = Ap550ConnectionPhase.DISCONNECTED,
                    statusText = rh.gs(R.string.ap550_status_disconnected),
                    description = rh.gs(R.string.ap550_tap_to_reconnect)
                )
        }
    }

    private fun buildOverview(snapshot: PumpSnapshot): PumpOverviewUiState {
        val pump = ap550Pump
        val isConfigured = ap550PumpPlugin.isConfigured()
        val isInitialized = ap550PumpPlugin.isInitialized()

        val agoMinutes = (System.currentTimeMillis() - snapshot.lastConnectionTime) / 60_000.0
        val lastConnection = if (snapshot.lastConnectionTime != 0L) {
            val agoText = when {
                agoMinutes > 3 * 24 * 60 -> rh.gs(InterfacesR.string.days_ago, 3.0)
                agoMinutes > 24 * 60     -> rh.gs(InterfacesR.string.days_ago, agoMinutes / 60 / 24)
                agoMinutes > 60          -> rh.gs(InterfacesR.string.hoursago, agoMinutes / 60)
                else                     -> rh.gs(InterfacesR.string.minago, agoMinutes.toInt())
            }
            dateUtil.timeString(snapshot.lastConnectionTime) + " (" + agoText + ")"
        } else ""

        val lastConnectionLevel = if (snapshot.lastConnectionTime != 0L) {
            when {
                agoMinutes >= 31 -> StatusLevel.CRITICAL
                agoMinutes >= 16 -> StatusLevel.WARNING
                else             -> StatusLevel.NORMAL
            }
        } else StatusLevel.UNSPECIFIED

        // The helper returns null when the bolus is older than six hours, which is the same cut off
        // the screen used before.
        val lastBolus = if (snapshot.lastBolusTime != null) {
            ch.insulinAmountAgoString(PumpInsulin(snapshot.lastBolusAmount ?: 0.0), snapshot.lastBolusTime)
        } else null

        val batteryText = snapshot.battery?.let { "$it%" }
        val batteryLevel = when {
            snapshot.battery == null -> StatusLevel.UNSPECIFIED
            snapshot.battery <= 26   -> StatusLevel.CRITICAL
            snapshot.battery <= 51   -> StatusLevel.WARNING
            else                     -> StatusLevel.NORMAL
        }

        val reservoirLevel = when {
            ch.fromPump(PumpInsulin(snapshot.reservoir)) <= 20.0 -> StatusLevel.CRITICAL
            ch.fromPump(PumpInsulin(snapshot.reservoir)) <= 50.0 -> StatusLevel.WARNING
            else                                                 -> StatusLevel.NORMAL
        }

        // These two formatters live on the pump object: they know whether a running temp basal is
        // shown as a percentage or as a rate.
        val tempBasalText = pump.temporaryBasalToString()
        val extendedBolusText = pump.extendedBolusToString()

        val infoRows = if (!isConfigured) emptyList() else buildList {
            pump.serialNumber.takeIf { it.isNotEmpty() }?.let {
                add(PumpInfoRow(label = rh.gs(R.string.serialnumber), value = it))
            }

            batteryText?.let {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.battery_label), value = it, level = batteryLevel))
            }

            if (lastConnection.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.last_connection_label), value = lastConnection, level = lastConnectionLevel))
            }

            lastBolus?.let {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.last_bolus_label), value = it))
            }

            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.daily_units),
                    value = ch.insulinAmountString(PumpInsulin(pump.dailyTotalUnits)),
                    level = when {
                        pump.dailyTotalUnits > pump.maxDailyTotalUnits * 0.9  -> StatusLevel.CRITICAL
                        pump.dailyTotalUnits > pump.maxDailyTotalUnits * 0.75 -> StatusLevel.WARNING
                        else                                                  -> StatusLevel.NORMAL
                    }
                )
            )
            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.max_daily_units),
                    value = ch.insulinAmountString(PumpInsulin(pump.maxDailyTotalUnits.toDouble()))
                )
            )

            add(PumpInfoRow(label = rh.gs(CoreUiR.string.base_basal_rate_label), value = rh.gs(CoreUiR.string.pump_base_basal_rate, pump.currentBasal)))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.tempbasal_label), value = tempBasalText, visible = tempBasalText.isNotEmpty()))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.extended_bolus_label), value = extendedBolusText, visible = extendedBolusText.isNotEmpty()))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = ch.insulinAmountString(PumpInsulin(snapshot.reservoir)), level = reservoirLevel))

            add(PumpInfoRow(label = rh.gs(R.string.basal_step), value = rh.gs(R.string.format_tb_rate_units, pump.basalStep)))
            add(PumpInfoRow(label = rh.gs(R.string.bolus_step), value = rh.gs(R.string.format_insulin_units_3, pump.bolusStep)))

            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.firmware),
                    value = rh.gs(R.string.ap550_model, pump.modelFriendlyName(), pump.pumpVersionCode1, pump.pumpVersionCode2)
                )
            )

            add(PumpInfoRow(label = rh.gs(R.string.maxBolus), value = rh.gs(R.string.format_insulin_units_3, pump.maxBolus)))
            add(PumpInfoRow(label = rh.gs(R.string.maxBasal), value = rh.gs(R.string.format_tb_rate_units, pump.maxBasal)))
        }

        // The old screen had no refresh button, and the button bar order was history, stop/resume,
        // connect status. User options exist as a screen but its entry button was always hidden
        // there, so no button is shown for it.
        val connectedAndPaired = ap550PumpPlugin.isConnected() && pump.paired

        val primaryActions = listOf(
            ScreenAction(
                label = rh.gs(CoreUiR.string.pump_history),
                icon = Icons.AutoMirrored.Filled.List,
                visible = isInitialized,
                onClick = { _events.tryEmit(Ap550OverviewEvent.StartHistory) }
            ),
            ScreenAction(
                label = rh.gs(if (pump.pumpSuspended) R.string.resume_pump_injection else R.string.stop_pump_injection),
                icon = Icons.Filled.Pause,
                visible = isInitialized && connectedAndPaired,
                onClick = { _events.tryEmit(Ap550OverviewEvent.ConfirmSuspendResume) }
            )
        )

        val managementActions = buildList {
            add(
                ScreenAction(
                    label = rh.gs(R.string.connect_status),
                    icon = Icons.Filled.History,
                    onClick = { _events.tryEmit(Ap550OverviewEvent.StartConnectStatus) }
                )
            )
            // Unpair lives on the user options screen, not here. Without a paired pump the pair
            // button stays, so there is still an entry into the pairing wizard.
            if (!isConfigured) {
                add(
                    ScreenAction(
                        label = rh.gs(CoreUiR.string.pump_pair),
                        icon = Icons.Filled.Bluetooth,
                        onClick = { _events.tryEmit(Ap550OverviewEvent.StartPairWizard) }
                    )
                )
            }
            add(
                ScreenAction(
                    label = rh.gs(if (pump.pumpMuteStatus) R.string.ap550_pump_sound_on_button else R.string.ap550_pump_sound_off_button),
                    icon = if (pump.pumpMuteStatus) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    visible = isInitialized && connectedAndPaired,
                    onClick = { _events.tryEmit(Ap550OverviewEvent.ConfirmMute) }
                )
            )
        }

        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            queueStatus = communicationStatus.queueStatus(),
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }

    /**
     * Tap on the pump card icon.
     *
     * Without a paired pump the tap opens the pairing screen. Otherwise it moves the connection one
     * step in the other direction: connect when disconnected, disconnect when connected.
     */
    fun onConnectionIconClick() {
        if (!isPumpBound()) {
            _events.tryEmit(Ap550OverviewEvent.StartPairWizard)
            return
        }
        // A connect attempt takes a moment. Taps inside that window would queue a second one.
        if (abs(System.currentTimeMillis() - ap550Pump.lastConnection) < CONNECT_THROTTLE_MS) {
            snackbar(rh.gs(R.string.ap550_connect_button_throttled))
            return
        }
        when {
            ap550PumpPlugin.isConnecting()   -> snackbar(rh.gs(R.string.ap550_connecting_wait))

            ap550PumpPlugin.isConnected()    -> viewModelScope.launch {
                ap550PumpPlugin.disconnectCommand()
                ap550PumpPlugin.disconnect("disconnect")
                // Give the link time to close before checking, like the old screen did.
                delay(DISCONNECT_SETTLE_MS)
                if (!ap550PumpPlugin.disconnected()) snackbar(rh.gs(R.string.ap550_disconnect_failed))
                ap550Pump.lastConnection = 0
            }

            ap550PumpPlugin.disconnecting()  -> snackbar(rh.gs(R.string.ap550_disconnecting_wait))

            preferences.get(Ap550StringNonKey.MacAddress).isEmpty() ->
                snackbar(rh.gs(R.string.ap550_bind_pump))

            else                             -> viewModelScope.launch {
                commandQueue.readStatus(rh.gs(CoreUiR.string.clicked_connect_to_pump))
                ap550Pump.lastConnection = 0
            }
        }
    }

    /**
     * Suspend ([suspend] true) or resume the pump.
     *
     * The plugin refuses this on firmware that cannot do it and when the pump is already in the
     * asked state. Both cases come back as a failed result with a ready made comment.
     */
    fun suspendResumePump(suspend: Boolean) {
        if (!ap550PumpPlugin.isConnected() || !ap550Pump.paired) {
            snackbar(rh.gs(R.string.ap550_pump_not_connected), EventShowSnackbar.Type.Error)
            return
        }
        val result = ap550PumpPlugin.stopResumePump(if (suspend) PUMP_STATUS_SUSPENDED else PUMP_STATUS_RUNNING)
        if (result.success) {
            snackbar(rh.gs(if (suspend) R.string.ap550_suspend_pump_ok else R.string.ap550_resume_pump_ok))
        } else {
            val fallback = if (suspend) R.string.ap550_suspend_pump_failed else R.string.ap550_resume_pump_failed
            snackbar(result.comment.ifEmpty { rh.gs(fallback) }, EventShowSnackbar.Type.Error)
        }
        refreshTrigger.value = System.currentTimeMillis()
    }

    /**
     * Switch the pump buzzer on or off.
     *
     * Firmware before DCN18 and version 2300 have a buzzer but cannot switch it over Bluetooth.
     * Nothing checks that further down, so it is checked here.
     */
    fun mutePump() {
        val version = ap550Pump.pumpVersionString
        if (version == rh.gs(R.string.ap550_version_before_DCN18) || version == OLD_MUTE_FIRMWARE) {
            snackbar(rh.gs(R.string.ap550_pump_version_too_low), EventShowSnackbar.Type.Error)
            return
        }
        if (!ap550PumpPlugin.isConnected() || !ap550Pump.paired) {
            snackbar(rh.gs(R.string.ap550_pump_not_connected), EventShowSnackbar.Type.Error)
            return
        }
        ap550PumpPlugin.mutePump()
    }

    private fun snackbar(message: String, type: EventShowSnackbar.Type = EventShowSnackbar.Type.Info) {
        rxBus.send(EventShowSnackbar(message, type))
    }

    companion object {

        /** Taps closer together than this are treated as one. */
        private const val CONNECT_THROTTLE_MS = 8_000L

        /** Time given to the Bluetooth link to close before the result is checked. */
        private const val DISCONNECT_SETTLE_MS = 500L

        /** While connecting, remind the user after every this many seconds. */
        private const val RECONNECT_PROMPT_SEC = 120

        private const val PUMP_STATUS_RUNNING = 0
        private const val PUMP_STATUS_SUSPENDED = 1

        /** Firmware that has a buzzer but cannot switch it over Bluetooth. */
        private const val OLD_MUTE_FIRMWARE = "2300"
    }
}