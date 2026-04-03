package app.aaps.pump.eopatch.compose

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewStateBuilder
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.extension.takeOne
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.TempBasalManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

sealed class EopatchOverviewEvent {
    data class StartPatchWorkflow(
        val startStep: PatchStep,
        val forceDiscard: Boolean = false,
        val isAlarmHandling: Boolean = false
    ) : EopatchOverviewEvent()

    data class ShowToast(val messageResId: Int, val isError: Boolean = false) : EopatchOverviewEvent()
}

@HiltViewModel
@Stable
class EopatchOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    val patchManager: IPatchManager,
    private val patchManagerExecutor: PatchManagerExecutor,
    private val patchConfigData: PatchConfig,
    private val tempBasalManager: TempBasalManager,
    private val normalBasalManager: NormalBasalManager,
    val preferenceManager: PreferenceManager,
    private val profileFunction: ProfileFunction,
    private val aapsSchedulers: AapsSchedulers,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateBuilder = PumpOverviewStateBuilder(rh)
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, scope)
    private val disposables = CompositeDisposable()

    private val _events = MutableSharedFlow<EopatchOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<EopatchOverviewEvent> = _events

    // Internal state flows bridged from RxJava
    private val _patchConfig = MutableStateFlow(patchConfigData)
    private val _bleConnectionState = MutableStateFlow(patchManagerExecutor.patchConnectionState)
    private val _isNormalBasalPaused = MutableStateFlow(preferenceManager.patchState.isNormalBasalPaused)
    private val _remainedInsulin = MutableStateFlow(preferenceManager.patchState.remainedInsulin)
    private val _stateUpdatedTimestamp = MutableStateFlow(preferenceManager.patchState.updatedTimestamp)

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        combine(_patchConfig, _bleConnectionState, _isNormalBasalPaused, _remainedInsulin) { config, conn, paused, insulin -> UiInputs(config, conn, paused, insulin) },
        _stateUpdatedTimestamp,
        tickerFlow(30_000L),
        communicationStatus.refreshTrigger
    ) { inputs, stateTs, _, _ ->
        buildUiState(inputs.config, inputs.connState, inputs.isPaused, inputs.insulin, stateTs)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), buildUiState(patchConfigData, patchManagerExecutor.patchConnectionState, preferenceManager.patchState.isNormalBasalPaused, preferenceManager.patchState.remainedInsulin, preferenceManager.patchState.updatedTimestamp))

    private data class UiInputs(val config: PatchConfig, val connState: BleConnectionState, val isPaused: Boolean, val insulin: Float)

    init {
        // Bridge RxJava → StateFlow
        disposables.add(
            preferenceManager.observePatchConfig()
                .observeOn(aapsSchedulers.main)
                .subscribe { _patchConfig.value = it }
        )

        disposables.add(
            preferenceManager.observePatchState()
                .observeOn(aapsSchedulers.main)
                .subscribe {
                    _remainedInsulin.value = it.remainedInsulin
                    _isNormalBasalPaused.value = it.isNormalBasalPaused
                    _stateUpdatedTimestamp.value = it.updatedTimestamp
                }
        )

        disposables.add(
            patchManagerExecutor.observePatchConnectionState()
                .observeOn(aapsSchedulers.main)
                .subscribe { _bleConnectionState.value = it }
        )

        disposables.add(
            preferenceManager.observePatchLifeCycle()
                .observeOn(aapsSchedulers.main)
                .subscribe {
                    // Trigger state refresh via patchConfig re-emit
                    _patchConfig.value = patchConfigData
                }
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
        scope.cancel()
    }

    fun onClickActivation() {
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            if (profile == null) {
                _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.no_profile_selected))
                return@launch
            }
            val basalValues = profile.getBasalValues()
            for (basalRate in basalValues) {
                if (basalRate.value < 0.049999) {
                    _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.invalid_basal_rate))
                    return@launch
                }
            }
            normalBasalManager.setNormalBasal(profile)
            preferenceManager.flushNormalBasalManager()
            _events.tryEmit(EopatchOverviewEvent.StartPatchWorkflow(PatchStep.WAKE_UP))
        }
    }

    fun onClickDeactivation() {
        _events.tryEmit(EopatchOverviewEvent.StartPatchWorkflow(PatchStep.SAFE_DEACTIVATION))
    }

    fun pauseBasal(pauseDurationHour: Float) {
        disposables.add(
            patchManagerExecutor.pauseBasal(pauseDurationHour)
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe({ response ->
                               if (response.isSuccess) {
                                   runBlocking {
                                       pumpSync.syncTemporaryBasalWithPumpId(
                                           timestamp = dateUtil.now(),
                                           rate = PumpRate(0.0),
                                           duration = T.mins((pauseDurationHour * 60).toLong()).msecs(),
                                           isAbsolute = true,
                                           type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                                           pumpId = dateUtil.now(),
                                           pumpType = PumpType.EOFLOW_EOPATCH2,
                                           pumpSerial = patchConfigData.patchSerialNumber
                                       )
                                   }
                                   _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.string_suspended_insulin_delivery_message))
                               } else {
                                   _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.string_pause_failed, isError = true))
                               }
                           }, {
                               _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.string_pause_failed, isError = true))
                           })
        )
    }

    fun resumeBasal() {
        disposables.add(
            patchManagerExecutor.resumeBasal()
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe({
                               if (it.isSuccess) {
                                   runBlocking {
                                       pumpSync.syncStopTemporaryBasalWithPumpId(
                                           timestamp = dateUtil.now(),
                                           endPumpId = dateUtil.now(),
                                           pumpType = PumpType.EOFLOW_EOPATCH2,
                                           pumpSerial = patchConfigData.patchSerialNumber
                                       )
                                   }
                                   _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.string_resumed_insulin_delivery_message))
                               } else {
                                   _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.string_resume_failed, isError = true))
                               }
                           }, {
                               _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.string_resume_failed, isError = true))
                           })
        )
    }

    fun getSuspendDialogText(): String {
        val isBolusActive = preferenceManager.patchState.isBolusActive
        val tempBasal = tempBasalManager.startedBasal
        val isTempBasalActive = preferenceManager.patchState.isTempBasalActive && tempBasal != null
        val tempRate = tempBasal?.doseUnitText ?: ""
        val tempRemainTime = tempBasal?.remainTimeText ?: ""
        var remainBolus = preferenceManager.patchState.isNowBolusActive.takeOne(preferenceManager.bolusCurrent.remain(BolusType.NOW), 0f)
        remainBolus += preferenceManager.patchState.isExtBolusActive.takeOne(preferenceManager.bolusCurrent.remain(BolusType.EXT), 0f)

        return when {
            isBolusActive && isTempBasalActive -> rh.gs(R.string.insulin_suspend_msg1, tempRate, tempRemainTime, remainBolus)
            isBolusActive                      -> rh.gs(R.string.insulin_suspend_msg2, remainBolus)
            isTempBasalActive                  -> rh.gs(R.string.insulin_suspend_msg3, tempRate, tempRemainTime)
            else                               -> rh.gs(R.string.insulin_suspend_msg4)
        }
    }

    private fun buildUiState(
        config: PatchConfig,
        connState: BleConnectionState,
        isPaused: Boolean,
        insulin: Float,
        stateUpdatedTimestamp: Long
    ): PumpOverviewUiState {
        // Status banner
        val statusBanner = buildStatusBanner(connState, config.isActivated, isPaused)

        // Info rows
        val commonRows = stateBuilder.buildCommonRows()
        val specificRows = buildList {
            // Connection state — only show when not connected (error states shown in banner)
            if (connState != BleConnectionState.CONNECTED) {
                val connText = when (connState) {
                    BleConnectionState.DISCONNECTED -> rh.gs(app.aaps.core.ui.R.string.disconnected)
                    else                            -> rh.gs(app.aaps.core.ui.R.string.connecting)
                }
                add(PumpInfoRow(label = rh.gs(R.string.eopatch_ble_status), value = connText))
            }

            // Patch status
            if (config.isActivated) {
                val statusText = if (isPaused) {
                    val finishTimeMillis = config.basalPauseFinishTimestamp
                    val remainTimeMillis = max(finishTimeMillis - System.currentTimeMillis(), 0L)
                    val h = TimeUnit.MILLISECONDS.toHours(remainTimeMillis)
                    val m = TimeUnit.MILLISECONDS.toMinutes(remainTimeMillis - TimeUnit.HOURS.toMillis(h))
                    "${rh.gs(app.aaps.core.ui.R.string.pumpsuspended)}\n${rh.gs(R.string.string_temp_basal_remained_hhmm, h.toString(), m.toString())}"
                } else {
                    rh.gs(R.string.string_running)
                }
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.status), value = statusText))

                // Basal rate
                if (preferenceManager.patchState.isNormalBasalRunning) {
                    val basalRate = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, normalBasalManager.normalBasal.currentSegmentDoseUnitPerHour)
                    add(PumpInfoRow(label = rh.gs(R.string.eopatch_base_basal_rate), value = basalRate))
                }

                // Temp basal
                val tempBasal = tempBasalManager.startedBasal
                if (preferenceManager.patchState.isTempBasalActive && tempBasal != null) {
                    val tempRate = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, tempBasal.doseUnitPerHour)
                    add(PumpInfoRow(label = rh.gs(R.string.eopatch_temp_basal_rate), value = tempRate))
                }
            }

            // Remaining insulin
            val insulinText = when {
                insulin > 50f -> "50+ U"
                insulin < 1f  -> "0 U"
                else          -> "${insulin.roundToInt()} U"
            }
            if (config.isActivated) {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.reservoir_label), value = insulinText))
            }

            // Serial number
            if (config.patchSerialNumber.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(R.string.eopatch_serial_number), value = config.patchSerialNumber))
            }

            // Last state update
            if (config.isActivated && stateUpdatedTimestamp > 0L) {
                add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_connection_label), value = dateUtil.minAgo(rh, stateUpdatedTimestamp)))
            }
        }

        // Actions
        val isActivated = config.isActivated
        val primaryActions = buildList {
            if (!isActivated) {
                add(
                    PumpAction(
                        label = rh.gs(R.string.string_activate_patch),
                        iconRes = app.aaps.core.ui.R.drawable.ic_swap_horiz,
                        category = ActionCategory.PRIMARY,
                        onClick = { onClickActivation() }
                    )
                )
            }
            if (isActivated) {
                add(
                    PumpAction(
                        label = if (isPaused) rh.gs(app.aaps.core.ui.R.string.pump_resume) else rh.gs(app.aaps.core.ui.R.string.pump_suspend),
                        iconRes = if (isPaused) app.aaps.core.ui.R.drawable.ic_loop_resume else app.aaps.core.ui.R.drawable.ic_loop_paused,
                        category = ActionCategory.PRIMARY,
                        onClick = { } // Click handled in screen composable (needs dialog)
                    )
                )
            }
        }

        val managementActions = buildList {
            if (isActivated) {
                add(
                    PumpAction(
                        label = rh.gs(R.string.string_discard_patch),
                        iconRes = app.aaps.core.ui.R.drawable.ic_swap_horiz,
                        category = ActionCategory.MANAGEMENT,
                        onClick = { onClickDeactivation() }
                    )
                )
            }
        }

        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner() ?: statusBanner,
            infoRows = commonRows + specificRows,
            primaryActions = primaryActions,
            managementActions = managementActions,
            queueStatus = communicationStatus.queueStatus()
        )
    }

    private fun buildStatusBanner(connState: BleConnectionState, isActivated: Boolean, isPaused: Boolean): StatusBanner? = when {
        !isActivated -> StatusBanner(text = rh.gs(R.string.eopatch_not_activated), level = StatusLevel.WARNING)
        isPaused -> StatusBanner(text = rh.gs(app.aaps.core.ui.R.string.pumpsuspended), level = StatusLevel.WARNING)
        connState == BleConnectionState.DISCONNECTED -> StatusBanner(text = rh.gs(app.aaps.core.ui.R.string.disconnected), level = StatusLevel.CRITICAL)
        connState != BleConnectionState.CONNECTED -> StatusBanner(text = rh.gs(app.aaps.core.ui.R.string.connecting), level = StatusLevel.UNSPECIFIED)
        else -> null // Connected, activated, running — no banner needed
    }
}
