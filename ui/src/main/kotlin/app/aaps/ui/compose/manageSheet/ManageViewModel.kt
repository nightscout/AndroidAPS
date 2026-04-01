package app.aaps.ui.compose.manageSheet

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.toStringMedium
import app.aaps.core.objects.extensions.toStringShort
import app.aaps.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val commandQueue: CommandQueue,
    private val uel: UserEntryLogger,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil
) : ViewModel() {

    val uiState: StateFlow<ManageUiState>
        field = MutableStateFlow(ManageUiState(pumpPlugin = activePlugin.activePumpInternal as PluginBase))

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
    }

    fun refreshState() {
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            val pump = activePlugin.activePump
            val pumpDescription = pump.pumpDescription
            val isInitialized = pump.isInitialized()
            val isSuspended = pump.isSuspended()
            val isDisconnected = loop.runningMode == RM.Mode.DISCONNECTED_PUMP
            loop.runningMode.isLoopRunning()

            // Extended bolus visibility
            val showExtendedBolus: Boolean
            val showCancelExtendedBolus: Boolean
            val cancelExtendedBolusText: String

            if (!pumpDescription.isExtendedBolusCapable || !isInitialized || isSuspended ||
                isDisconnected || pump.isFakingTempsByExtendedBoluses || config.AAPSCLIENT
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
                    showCancelExtendedBolus = true
                    cancelExtendedBolusText = rh.gs(R.string.cancel) + " " +
                        activeExtendedBolus.toStringMedium(dateUtil, rh)
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
                isDisconnected || config.AAPSCLIENT
            ) {
                showTempBasal = false
                showCancelTempBasal = false
                cancelTempBasalText = ""
            } else {
                val activeTemp = processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())
                if (activeTemp != null) {
                    showTempBasal = false
                    showCancelTempBasal = true
                    cancelTempBasalText = rh.gs(R.string.cancel) + " " +
                        activeTemp.toStringShort(rh)
                } else {
                    showTempBasal = true
                    showCancelTempBasal = false
                    cancelTempBasalText = ""
                }
            }

            // Custom actions
            val customActions = pump.getCustomActions()?.filter { it.isEnabled } ?: emptyList()

            uiState.update { state ->
                state.copy(
                    showTempTarget = true,
                    showTempBasal = showTempBasal,
                    showCancelTempBasal = showCancelTempBasal,
                    showExtendedBolus = showExtendedBolus,
                    showCancelExtendedBolus = showCancelExtendedBolus,
                    showHistoryBrowser = profile != null,
                    cancelTempBasalText = cancelTempBasalText,
                    cancelExtendedBolusText = cancelExtendedBolusText,
                    isPatchPump = pumpDescription.isPatchPump,
                    pumpPlugin = activePlugin.activePumpInternal as PluginBase,
                    customActions = customActions
                )
            }
        }
    }

    // Action handlers
    fun cancelTempBasal(onResult: (Boolean, String) -> Unit) {
        if (processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis()) != null) {
            uel.log(Action.CANCEL_TEMP_BASAL, Sources.Actions)
            commandQueue.cancelTempBasal(enforceNew = true, callback = object : Callback() {
                override fun run() {
                    onResult(result.success, result.comment)
                }
            })
        }
    }

    fun cancelExtendedBolus(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val activeExtended = withContext(Dispatchers.IO) {
                persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())
            }
            if (activeExtended != null) {
                uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.Actions)
                commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        onResult(result.success, result.comment)
                    }
                })
            }
        }
    }

    fun executeCustomAction(actionType: CustomActionType) {
        activePlugin.activePump.executeCustomAction(actionType)
    }

    fun copyStatusLightsFromNightscout() {
        activePlugin.activeOverview.applyStatusLightsFromNs(null)
    }
}