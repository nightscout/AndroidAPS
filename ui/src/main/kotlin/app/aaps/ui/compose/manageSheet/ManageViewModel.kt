package app.aaps.ui.compose.manageSheet

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.extensions.toStringMedium
import app.aaps.core.objects.extensions.toStringShort
import app.aaps.core.ui.R
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.ui.R as UiR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@Stable
class ManageViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val config: Config,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val persistenceLayer: PersistenceLayer,
    private val uel: UserEntryLogger,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val nsSettingStatus: NSSettingsStatus,
    private val preferences: Preferences,
    private val batchExecutor: BatchExecutor,
    private val nsClient: NsClient,
    private val visibilityContext: VisibilityContext,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageUiState(pumpPlugin = activePlugin.activePumpInternal as PluginBase))
    val uiState: StateFlow<ManageUiState> = _uiState.asStateFlow()

    sealed interface SideEffect {

        /** The MASTER prepared the cancel and returned its confirmation [lines]; show the [elementType] dialog, then [commitCancel] [bolusId]. [label] = the round-trip modal title reused on commit. */
        data class ShowConfirmation(val elementType: ElementType, val bolusId: Long, val lines: List<ConfirmationLine>, val label: String) : SideEffect

        /** A prepare/commit was rejected (offline block, or a master-local failure) — surface it as an alarm for [elementType]. */
        data class ShowError(val elementType: ElementType, val comment: String) : SideEffect
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        setupEventListeners()
        refreshState()
    }

    private fun setupEventListeners() {
        rxBus.toFlow(EventInitializationChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        persistenceLayer.observeChanges(EB::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        persistenceLayer.observeChanges(TB::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventCustomActionsChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        // Re-evaluate showMutatingActions when the client pairs/unpairs (stable signal, flips rarely).
        nsClient.masterOrPairedClientFlow
            .onEach { refreshState() }.launchIn(viewModelScope)
    }

    fun refreshState() {
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            val pump = activePlugin.activePump
            val pumpDescription = pump.pumpDescription
            val isInitialized = pump.isInitialized()
            val isSuspended = pump.isSuspended()
            val runningMode = loop.runningMode()
            val isDisconnected = runningMode == RM.Mode.DISCONNECTED_PUMP
            runningMode.isLoopRunning()

            // Extended bolus visibility
            val showExtendedBolus: Boolean
            val showCancelExtendedBolus: Boolean
            val cancelExtendedBolusText: String

            if (!pumpDescription.isExtendedBolusCapable || !isInitialized || isSuspended ||
                isDisconnected || pump.isFakingTempsByExtendedBoluses
            ) {
                showExtendedBolus = false
                showCancelExtendedBolus = false
                cancelExtendedBolusText = ""
            } else {
                val activeExtendedBolus = withContext(Dispatchers.IO) {
                    persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())
                }
                if (activeExtendedBolus != null) {
                    showExtendedBolus = false
                    showCancelExtendedBolus = true // cancel relays to the master via batchExecutor → shown on a client too
                    cancelExtendedBolusText = rh.gs(UiR.string.cancel_action_with_details, rh.gs(R.string.cancel), activeExtendedBolus.toStringMedium(dateUtil, rh))
                } else {
                    showExtendedBolus = true
                    showCancelExtendedBolus = false
                    cancelExtendedBolusText = ""
                }
            }

            // Temp basal visibility
            val showTempBasal: Boolean
            val showCancelTempBasal: Boolean
            val cancelTempBasalText: String

            if (!pumpDescription.isTempBasalCapable || !isInitialized || isSuspended ||
                isDisconnected
            ) {
                showTempBasal = false
                showCancelTempBasal = false
                cancelTempBasalText = ""
            } else {
                val activeTemp = withContext(Dispatchers.IO) {
                    processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())
                }
                if (activeTemp != null) {
                    showTempBasal = false
                    showCancelTempBasal = true // cancel relays to the master via batchExecutor → shown on a client too
                    cancelTempBasalText = rh.gs(UiR.string.cancel_action_with_details, rh.gs(R.string.cancel), activeTemp.toStringShort(rh))
                } else {
                    showTempBasal = true
                    showCancelTempBasal = false
                    cancelTempBasalText = ""
                }
            }

            // Custom actions
            val customActions = pump.getCustomActions()?.filter { it.isEnabled } ?: emptyList()

            _uiState.update { state ->
                state.copy(
                    showTempTarget = true,
                    showTempBasal = showTempBasal,
                    showCancelTempBasal = showCancelTempBasal,
                    showExtendedBolus = showExtendedBolus,
                    showCancelExtendedBolus = showCancelExtendedBolus,
                    showHistoryBrowser = profile != null,
                    showBatteryChange = pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled(),
                    showFill = pumpDescription.isRefillingCapable && isInitialized,
                    showAuthorizedClients = preferences.get(BooleanKey.NsClient3UseWs) && !config.AAPSCLIENT,
                    showPairWithMaster = config.AAPSCLIENT && preferences.get(BooleanKey.NsClient3UseWs),
                    showMutatingActions = !config.AAPSCLIENT || nsClient.masterOrPairedClientFlow.value,
                    // General gate: PUMP declares its own visibility (!isClient → full + pumpcontrol, not aapsclient).
                    showPump = ElementType.PUMP.visibility.isVisible(visibilityContext),
                    cancelTempBasalText = cancelTempBasalText,
                    cancelExtendedBolusText = cancelExtendedBolusText,
                    isPatchPump = pumpDescription.isPatchPump,
                    pumpPlugin = activePlugin.activePumpInternal as PluginBase,
                    customActions = customActions
                )
            }
        }
    }

    // Action handlers — cancel goes through the MASTER (batchExecutor): the master validates its pump, authors the
    // confirmation, and applies the cancel on its own pump. On a client this is the signed round-trip; on a master it's
    // local. The uel log + commandQueue.cancel now live in the executor (the single master-side apply point).
    fun cancelTempBasal() = prepareCancel(BatchAction.CancelTempBasal, ElementType.TEMP_BASAL, rh.gs(R.string.tempbasal_label))

    fun cancelExtendedBolus() = prepareCancel(BatchAction.CancelExtendedBolus, ElementType.EXTENDED_BOLUS, rh.gs(R.string.extended_bolus))

    /**
     * Ask the MASTER to PREPARE the cancel and return its confirmation [lines]; the user then confirms and [commitCancel]
     * applies it. appScope: the sheet may dismiss while the round-trip is in flight (cancelling viewModelScope).
     */
    private fun prepareCancel(action: BatchAction, elementType: ElementType, label: String) {
        appScope.launch {
            when (val prepared = batchExecutor.prepare(listOf(action), Sources.Actions, label)) {
                is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(elementType, prepared.id, prepared.lines, label))
                // Offline block (and a master-local failure) surface here; a client round-trip failure already showed on the modal.
                is ActionProgress.Rejected ->
                    if (prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled) rxBus.send(EventShowDialog.Ok(title = label, message = rh.gs(prepared.reason.failTextResId())))
                    else prepared.detail?.let { detail ->
                        if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = label, message = detail))
                        else _sideEffect.tryEmit(SideEffect.ShowError(elementType, detail))
                    }

                else                       -> Unit // Unconfirmed → app-level modal
            }
        }
    }

    /** Confirm the master's prepared cancel: apply it exactly once. A master-local apply failure (pump comms) surfaces here. */
    fun commitCancel(bolusId: Long, elementType: ElementType, label: String) {
        appScope.launch {
            // NoPendingBolus (a double-tapped dialog already consumed it) stays silent — the cancel ran once.
            val result = batchExecutor.commit(bolusId, Sources.Actions, label, pumpDirect = true)
            if (result is ActionProgress.Rejected)
                if (result.reason == FailureReason.NotReachable || result.reason == FailureReason.ControlDisabled) rxBus.send(EventShowDialog.Ok(title = label, message = rh.gs(result.reason.failTextResId())))
                else result.detail?.let { detail ->
                    if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = label, message = detail))
                    else _sideEffect.tryEmit(SideEffect.ShowError(elementType, detail))
                }
        }
    }

    fun executeCustomAction(actionType: CustomActionType) {
        activePlugin.activePump.executeCustomAction(actionType)
    }

    fun copyStatusLightsFromNightscout() {
        val cageWarn = nsSettingStatus.getExtendedWarnValue("cage", "warn")?.toInt()
        val cageCritical = nsSettingStatus.getExtendedWarnValue("cage", "urgent")?.toInt()
        val iageWarn = nsSettingStatus.getExtendedWarnValue("iage", "warn")?.toInt()
        val iageCritical = nsSettingStatus.getExtendedWarnValue("iage", "urgent")?.toInt()
        val sageWarn = nsSettingStatus.getExtendedWarnValue("sage", "warn")?.toInt()
        val sageCritical = nsSettingStatus.getExtendedWarnValue("sage", "urgent")?.toInt()
        val bageWarn = nsSettingStatus.getExtendedWarnValue("bage", "warn")?.toInt()
        val bageCritical = nsSettingStatus.getExtendedWarnValue("bage", "urgent")?.toInt()
        cageWarn?.let { preferences.put(IntKey.OverviewCageWarning, it) }
        cageCritical?.let { preferences.put(IntKey.OverviewCageCritical, it) }
        iageWarn?.let { preferences.put(IntKey.OverviewIageWarning, it) }
        iageCritical?.let { preferences.put(IntKey.OverviewIageCritical, it) }
        sageWarn?.let { preferences.put(IntKey.OverviewSageWarning, it) }
        sageCritical?.let { preferences.put(IntKey.OverviewSageCritical, it) }
        bageWarn?.let { preferences.put(IntKey.OverviewBageWarning, it) }
        bageCritical?.let { preferences.put(IntKey.OverviewBageCritical, it) }
        uel.log(Action.NS_SETTINGS_COPIED, Sources.NSClient)
    }
}