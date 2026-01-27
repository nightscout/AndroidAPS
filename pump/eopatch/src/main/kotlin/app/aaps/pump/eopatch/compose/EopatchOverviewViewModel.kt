package app.aaps.pump.eopatch.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
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
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateBuilder = PumpOverviewStateBuilder(rh)
    private val disposables = CompositeDisposable()

    private val _events = MutableSharedFlow<EopatchOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<EopatchOverviewEvent> = _events

    // Internal state flows bridged from RxJava
    private val _patchConfig = MutableStateFlow(patchConfigData)
    private val _bleConnectionState = MutableStateFlow(patchManagerExecutor.patchConnectionState)
    private val _isNormalBasalPaused = MutableStateFlow(preferenceManager.patchState.isNormalBasalPaused)
    private val _remainedInsulin = MutableStateFlow(preferenceManager.patchState.remainedInsulin)

    val isPatchConnected: Boolean
        get() = patchManagerExecutor.patchConnectionState.isConnected

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        _patchConfig,
        _bleConnectionState,
        _isNormalBasalPaused,
        _remainedInsulin,
        tickerFlow(30_000L)
    ) { config, connState, isPaused, insulin, _ ->
        buildUiState(config, connState, isPaused, insulin)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), buildUiState(patchConfigData, patchManagerExecutor.patchConnectionState, preferenceManager.patchState.isNormalBasalPaused, preferenceManager.patchState.remainedInsulin))

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
        val profile = profileFunction.getProfile()
        if (profile == null) {
            _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.no_profile_selected))
            return
        }
        val basalValues = profile.getBasalValues()
        for (basalRate in basalValues) {
            if (basalRate.value < 0.049999) {
                _events.tryEmit(EopatchOverviewEvent.ShowToast(R.string.invalid_basal_rate))
                return
            }
        }
        normalBasalManager.setNormalBasal(profile)
        preferenceManager.flushNormalBasalManager()
        _events.tryEmit(EopatchOverviewEvent.StartPatchWorkflow(PatchStep.WAKE_UP))
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
                                   pumpSync.syncStopTemporaryBasalWithPumpId(
                                       timestamp = dateUtil.now(),
                                       endPumpId = dateUtil.now(),
                                       pumpType = PumpType.EOFLOW_EOPATCH2,
                                       pumpSerial = patchConfigData.patchSerialNumber
                                   )
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
        val isTempBasalActive = preferenceManager.patchState.isTempBasalActive
        val tempRate = tempBasalManager.startedBasal?.doseUnitText ?: ""
        val tempRemainTime = tempBasalManager.startedBasal?.remainTimeText ?: ""
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
        insulin: Float
    ): PumpOverviewUiState {
        // Status banner
        val statusBanner = buildStatusBanner(connState, config.isActivated, isPaused)

        // Info rows
        val commonRows = stateBuilder.buildCommonRows()
        val specificRows = buildList {
            // Connection state
            val connText = when (connState) {
                BleConnectionState.CONNECTED    -> rh.gs(app.aaps.core.interfaces.R.string.connected)
                BleConnectionState.DISCONNECTED -> rh.gs(app.aaps.core.ui.R.string.disconnected)
                else                            -> rh.gs(R.string.string_connecting)
            }
            add(PumpInfoRow(label = rh.gs(R.string.eopatch_ble_status), value = connText))

            // Patch status
            if (config.isActivated) {
                val statusText = if (isPaused) {
                    val finishTimeMillis = config.basalPauseFinishTimestamp
                    val remainTimeMillis = max(finishTimeMillis - System.currentTimeMillis(), 0L)
                    val h = TimeUnit.MILLISECONDS.toHours(remainTimeMillis)
                    val m = TimeUnit.MILLISECONDS.toMinutes(remainTimeMillis - TimeUnit.HOURS.toMillis(h))
                    "${rh.gs(R.string.string_suspended)}\n${rh.gs(R.string.string_temp_basal_remained_hhmm, h.toString(), m.toString())}"
                } else {
                    rh.gs(R.string.string_running)
                }
                add(PumpInfoRow(label = rh.gs(R.string.eopatch_status), value = statusText))

                // Basal rate
                if (preferenceManager.patchState.isNormalBasalRunning) {
                    val basalRate = "${normalBasalManager.normalBasal.currentSegmentDoseUnitPerHour} U/hr"
                    add(PumpInfoRow(label = rh.gs(R.string.eopatch_base_basal_rate), value = basalRate))
                }

                // Temp basal
                if (preferenceManager.patchState.isTempBasalActive) {
                    val tempRate = "${tempBasalManager.startedBasal?.doseUnitPerHour} U/hr"
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
                        label = if (isPaused) rh.gs(R.string.string_resume) else rh.gs(R.string.string_suspend),
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
            statusBanner = statusBanner,
            infoRows = commonRows + specificRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }

    private fun buildStatusBanner(connState: BleConnectionState, isActivated: Boolean, isPaused: Boolean): StatusBanner {
        val connText = when (connState) {
            BleConnectionState.CONNECTED    -> rh.gs(app.aaps.core.interfaces.R.string.connected)
            BleConnectionState.DISCONNECTED -> rh.gs(app.aaps.core.ui.R.string.disconnected)
            else                            -> rh.gs(R.string.string_connecting)
        }
        return when {
            !isActivated -> StatusBanner(text = "$connText - ${rh.gs(R.string.eopatch_not_activated)}", level = StatusLevel.WARNING)
            isPaused     -> StatusBanner(text = "$connText - ${rh.gs(R.string.string_suspended)}", level = StatusLevel.WARNING)
            else         -> StatusBanner(text = connText, level = StatusLevel.NORMAL)
        }
    }
}
