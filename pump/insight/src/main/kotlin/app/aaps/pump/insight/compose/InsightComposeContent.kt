package app.aaps.pump.insight.compose

import android.content.Context
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.insight.R
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.events.EventLocalInsightUpdateGUI
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

private enum class InsightScreen { OVERVIEW, PAIR_WIZARD }

class InsightComposeContent(
    private val insightPlugin: InsightPlugin,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val commandQueue: CommandQueue,
    private val context: Context,
    private val aapsSchedulers: AapsSchedulers,
    private val pumpSync: PumpSync,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewState = remember {
            InsightOverviewState(
                insightPlugin = insightPlugin,
                rh = rh,
                rxBus = rxBus,
                dateUtil = dateUtil,
                commandQueue = commandQueue,
                context = context,
                aapsSchedulers = aapsSchedulers
            )
        }

        DisposableEffect(overviewState) {
            overviewState.start()
            onDispose { overviewState.stop() }
        }

        var currentScreen by remember { mutableStateOf(InsightScreen.OVERVIEW) }
        var showUnpairDialog by remember { mutableStateOf(false) }

        val pluginName = stringResource(id = R.string.insight_local)
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = app.aaps.core.ui.R.string.back)
                )
            }
        }
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = InsightScreen.OVERVIEW }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = app.aaps.core.ui.R.string.back)
                )
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(id = app.aaps.core.ui.R.string.settings)
                    )
                }
            }
        }
        val pairingTitle = stringResource(id = R.string.insight_pairing)
        LaunchedEffect(currentScreen) {
            setToolbarConfig(
                when (currentScreen) {
                    InsightScreen.OVERVIEW    -> ToolbarConfig(
                        title = pluginName,
                        navigationIcon = overviewNavIcon,
                        actions = settingsAction
                    )

                    InsightScreen.PAIR_WIZARD -> ToolbarConfig(
                        title = pairingTitle,
                        navigationIcon = subScreenNavIcon,
                        actions = {}
                    )
                }
            )
        }

        LaunchedEffect(overviewState) {
            overviewState.events.collect { event ->
                when (event) {
                    InsightOverviewEvent.RequestUnpair -> showUnpairDialog = true
                    InsightOverviewEvent.StartPairing  -> currentScreen = InsightScreen.PAIR_WIZARD
                }
            }
        }

        if (showUnpairDialog) {
            OkCancelDialog(
                title = pluginName,
                icon = Icons.Filled.Bluetooth,
                message = stringResource(id = R.string.reset_pairing),
                onConfirm = {
                    showUnpairDialog = false
                    overviewState.performUnpair()
                },
                onDismiss = { showUnpairDialog = false }
            )
        }

        when (currentScreen) {
            InsightScreen.OVERVIEW    -> {
                val uiState by overviewState.uiState.collectAsStateWithLifecycle()
                PumpOverviewScreen(state = uiState)
            }

            InsightScreen.PAIR_WIZARD -> {
                PairWizardHost(
                    blePreCheck = blePreCheck,
                    pumpSync = pumpSync,
                    onFinish = { currentScreen = InsightScreen.OVERVIEW }
                )
            }
        }
    }
}

@Composable
private fun PairWizardHost(
    blePreCheck: BlePreCheck,
    pumpSync: PumpSync,
    onFinish: () -> Unit
) {
    var bleReady by remember { mutableStateOf(false) }

    BlePreCheckHost(
        blePreCheck = blePreCheck,
        onReady = { bleReady = true },
        onFailed = onFinish
    )

    if (!bleReady) return

    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val pairState = remember {
        InsightPairState(
            context = context,
            pumpSync = pumpSync,
            scope = coroutineScope
        )
    }

    DisposableEffect(pairState) {
        pairState.start()
        onDispose { pairState.stop() }
    }

    LaunchedEffect(pairState) {
        pairState.events.collect { event ->
            when (event) {
                InsightPairStateEvent.Finish -> onFinish()
            }
        }
    }

    val wizardState by pairState.uiState.collectAsStateWithLifecycle()
    InsightPairWizardScreen(
        state = wizardState,
        onDeviceSelected = pairState::onDeviceSelected,
        onConfirmCode = pairState::onConfirmCode,
        onRejectCode = pairState::onRejectCode,
        onExit = onFinish,
        onCancel = onFinish
    )
}

internal sealed class InsightOverviewEvent {
    data object RequestUnpair : InsightOverviewEvent()
    data object StartPairing : InsightOverviewEvent()
}

internal class InsightOverviewState(
    private val insightPlugin: InsightPlugin,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val commandQueue: CommandQueue,
    @Suppress("unused") private val context: Context,
    private val aapsSchedulers: AapsSchedulers
) {

    private val disposable = CompositeDisposable()
    private var refreshPending = false
    private var tbrOverNotificationPending = false

    private val _events = MutableSharedFlow<InsightOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<InsightOverviewEvent> = _events

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<PumpOverviewUiState> = _uiState

    fun start() {
        disposable.add(
            rxBus.toObservable(EventLocalInsightUpdateGUI::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ refresh() }, {})
        )
        refresh()
    }

    fun stop() {
        disposable.clear()
    }

    fun performUnpair() {
        insightPlugin.connectionService?.reset()
        refresh()
    }

    private fun refresh() {
        _uiState.value = buildUiState()
    }

    private fun buildUiState(): PumpOverviewUiState {
        val service = insightPlugin.connectionService
        val isInitialized = insightPlugin.isInitialized()
        return PumpOverviewUiState(
            infoRows = if (service != null) buildInfoRows(service, isInitialized) else emptyList(),
            primaryActions = buildPrimaryActions(isInitialized),
            managementActions = buildManagementActions(service)
        )
    }

    private fun buildInfoRows(service: InsightConnectionService, isInitialized: Boolean): List<PumpInfoRow> = buildList {
        // Connection status
        val statusRes = when (service.state) {
            InsightState.NOT_PAIRED   -> R.string.not_paired
            InsightState.DISCONNECTED -> app.aaps.core.ui.R.string.disconnected
            InsightState.CONNECTED    -> app.aaps.core.interfaces.R.string.connected
            InsightState.RECOVERING   -> R.string.recovering
            else                      -> app.aaps.core.ui.R.string.connecting
        }
        add(PumpInfoRow(label = rh.gs(R.string.insight_status), value = rh.gs(statusRes)))

        if (service.state == InsightState.RECOVERING) {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.recovery_duration),
                    value = rh.gs(app.aaps.core.ui.R.string.secs, (service.recoveryDuration / 1000).toInt())
                )
            )
        }

        // Last connected (only when not currently connected and not unpaired)
        if (service.state != InsightState.CONNECTED && service.state != InsightState.NOT_PAIRED && service.lastConnected > 0L) {
            val minAgo = (System.currentTimeMillis() - service.lastConnected) / 60.0 / 1000.0
            val ago = if (minAgo < 60) dateUtil.minAgo(rh, service.lastConnected)
            else dateUtil.hourAgo(service.lastConnected, rh)
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.last_connected),
                    value = rh.gs(R.string.last_connection, dateUtil.timeString(service.lastConnected), ago)
                )
            )
        }

        if (isInitialized) {
            insightPlugin.operatingMode?.let { mode ->
                val modeText = when (mode) {
                    OperatingMode.STARTED -> rh.gs(R.string.started)
                    OperatingMode.STOPPED -> rh.gs(R.string.stopped)
                    OperatingMode.PAUSED  -> rh.gs(app.aaps.core.ui.R.string.paused)
                }
                add(PumpInfoRow(label = rh.gs(R.string.operating_mode), value = modeText))
            }

            insightPlugin.batteryStatus?.let { battery ->
                add(
                    PumpInfoRow(
                        label = rh.gs(app.aaps.core.ui.R.string.battery_label),
                        value = rh.gs(app.aaps.core.ui.R.string.format_percent, battery.batteryAmount)
                    )
                )
            }

            insightPlugin.cartridgeStatus?.let { cartridge ->
                val status = if (cartridge.isInserted)
                    rh.gs(app.aaps.core.ui.R.string.format_insulin_units, cartridge.remainingAmount)
                else
                    rh.gs(R.string.not_inserted)
                add(PumpInfoRow(label = rh.gs(R.string.reservoir_level), value = status))
            }

            insightPlugin.totalDailyDose?.let { tdd ->
                add(PumpInfoRow(label = rh.gs(R.string.tdd_bolus), value = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd.bolus)))
                add(PumpInfoRow(label = rh.gs(R.string.tdd_basal), value = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd.basal)))
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.tdd_total), value = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd.bolusAndBasal)))
            }

            insightPlugin.activeBasalRate?.let { basal ->
                add(
                    PumpInfoRow(
                        label = rh.gs(R.string.active_basal_rate),
                        value = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, basal.activeBasalRate) + " (${basal.activeBasalProfileName})"
                    )
                )
            }

            insightPlugin.activeTBR?.let { tbr ->
                add(
                    PumpInfoRow(
                        label = rh.gs(R.string.active_tbr),
                        value = rh.gs(R.string.tbr_formatter, tbr.percentage, tbr.initialDuration - tbr.remainingDuration, tbr.initialDuration)
                    )
                )
            }

            val lastBolusCu = insightPlugin.lastBolusAmount.value?.cU ?: 0.0
            if (lastBolusCu != 0.0 && insightPlugin.lastBolusTimestamp != 0L) {
                val minAgo = (System.currentTimeMillis() - insightPlugin.lastBolusTimestamp) / 60.0 / 1000.0
                val ago = if (minAgo < 60) dateUtil.minAgo(rh, insightPlugin.lastBolusTimestamp)
                else dateUtil.hourAgo(insightPlugin.lastBolusTimestamp, rh)
                val unit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                add(
                    PumpInfoRow(
                        label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label),
                        value = rh.gs(R.string.insight_last_bolus_formater, lastBolusCu, unit, ago)
                    )
                )
            }

            insightPlugin.activeBoluses?.let { boluses ->
                boluses.take(2).forEach { bolus ->
                    val label = when (bolus.bolusType) {
                        BolusType.MULTIWAVE -> rh.gs(R.string.multiwave_bolus)
                        BolusType.EXTENDED  -> rh.gs(app.aaps.core.ui.R.string.extended_bolus)
                        else                -> null
                    }
                    if (label != null) {
                        add(
                            PumpInfoRow(
                                label = label,
                                value = rh.gs(R.string.eb_formatter, bolus.remainingAmount, bolus.initialAmount, bolus.remainingDuration)
                            )
                        )
                    }
                }
            }
        }

        // Pump identity rows (from former InsightPairingInformationActivity)
        if (service.isPaired) {
            service.pumpSystemIdentification?.let { sys ->
                if (sys.serialNumber.isNotEmpty()) {
                    add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.serial_number), value = sys.serialNumber))
                }
                sys.manufacturingDate?.let { date ->
                    add(PumpInfoRow(label = rh.gs(R.string.manufacturing_date), value = date))
                }
            }
            service.pumpFirmwareVersions?.releaseSWVersion?.let { version ->
                add(PumpInfoRow(label = rh.gs(R.string.release_software_version), value = version))
            }
            service.bluetoothAddress?.let {
                add(PumpInfoRow(label = rh.gs(R.string.bluetooth_address), value = it))
            }
        }
    }

    private fun buildPrimaryActions(isInitialized: Boolean): List<PumpAction> = buildList {
        if (!isInitialized) return@buildList

        add(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                iconRes = app.aaps.core.ui.R.drawable.ic_refresh,
                category = ActionCategory.PRIMARY,
                enabled = !refreshPending,
                onClick = {
                    refreshPending = true
                    refresh()
                    commandQueue.readStatus("InsightRefreshButton", object : Callback() {
                        override fun run() {
                            refreshPending = false
                            refresh()
                        }
                    })
                }
            )
        )

        insightPlugin.tBROverNotificationBlock?.let { block ->
            val labelRes = if (block.isEnabled) R.string.disable_tbr_over_notification else R.string.enable_tbr_over_notification
            add(
                PumpAction(
                    label = rh.gs(labelRes),
                    icon = Icons.Filled.Notifications,
                    category = ActionCategory.PRIMARY,
                    enabled = !tbrOverNotificationPending,
                    onClick = {
                        tbrOverNotificationPending = true
                        refresh()
                        commandQueue.setTBROverNotification(object : Callback() {
                            override fun run() {
                                tbrOverNotificationPending = false
                                refresh()
                            }
                        }, !block.isEnabled)
                    }
                )
            )
        }
    }

    private fun buildManagementActions(service: InsightConnectionService?): List<PumpAction> = buildList {
        if (service == null) return@buildList
        if (service.isPaired) {
            add(
                PumpAction(
                    label = rh.gs(R.string.unpair),
                    iconRes = app.aaps.core.ui.R.drawable.ic_bluetooth_white_48dp,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { _events.tryEmit(InsightOverviewEvent.RequestUnpair) }
                )
            )
        } else {
            add(
                PumpAction(
                    label = rh.gs(R.string.insight_pairing),
                    iconRes = app.aaps.core.ui.R.drawable.ic_bluetooth_white_48dp,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { _events.tryEmit(InsightOverviewEvent.StartPairing) }
                )
            )
        }
    }
}
