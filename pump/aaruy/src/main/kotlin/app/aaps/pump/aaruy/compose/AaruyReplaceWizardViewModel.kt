package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class AaruyReplaceWizardStep {
    REPLACE_STEP_PUSH_BACK,
    REPLACE_STEP_POSITION,
    REPLACE_STEP_CONNECT_BODY,
    REPLACE_STEP_FILLING
}

data class AaruyReplaceWizardUiState(
    val step: AaruyReplaceWizardStep = AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK,
    val steps: List<AaruyReplaceWizardStep> = AaruyReplaceWizardStep.entries.filter { it != AaruyReplaceWizardStep.REPLACE_STEP_FILLING && it != AaruyReplaceWizardStep.REPLACE_STEP_POSITION },
)

sealed class AaruyReplaceWizardEvent {
    data object RepalceFinish : AaruyReplaceWizardEvent()
}

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyReplaceWizardViewModel(
    private val aapsLogger: AAPSLogger,
    private val aaruyPlugin: AaruyPlugin,
    private val aaruyPump: AaruyPump,
    private val aaruyBle: AaruyBLE
) : ViewModel() {

    val aaruyService: AaruyService?
        get() = aaruyPlugin.getService()

    private val _uiState = MutableStateFlow(AaruyReplaceWizardUiState())
    val uiState: StateFlow<AaruyReplaceWizardUiState> = _uiState

    private val _events = MutableSharedFlow<AaruyReplaceWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<AaruyReplaceWizardEvent> = _events

    val isBleConnect = mutableStateOf(false)
    val currentStatus = mutableIntStateOf(0)
    val showLoading = mutableStateOf(false)
    val showOverlay = mutableStateOf(false)
    val showOverlayText = mutableStateOf("")
    val showOverlayTipText = mutableStateOf("")
    val currentPointerId = mutableIntStateOf(-1)
    var isPushLocateReply = mutableStateOf(false)

    val isRewinding = mutableStateOf(false)
    val isRewindFinish = mutableStateOf(false)
    val isRemoveAir = mutableStateOf(false)
    private var isRunStatus6 = false
    val isRemoveAirFinish = mutableStateOf(false)   // 执行了充盈或者跳过 用于泵运行状态更新不及时导致的界面刷新问题
    val isPressLocate = mutableStateOf(false)
    val isLocateFinish = mutableStateOf(false)      // 推杆定位是否完成
    val is6mm = mutableStateOf(true)

    private val scope = CoroutineScope(Dispatchers.Default)

    private val errObserver = Observer<Int> { it ->
        if ((it shr 8) == 0) {
            when ((it and 0xff).toByte()) {
                AaruyConst.errCodeType.FLAG_PUSH_BACK_STATUS            -> {
                    isRewinding.value = true
                }

                AaruyConst.errCodeType.DISCHARGE_LIQUID                 -> {

                }

                AaruyConst.errCodeType.FLAG_LOCATE_POS_LIQUID_DISCHARGE -> {
                    isPressLocate.value = false
                }

                AaruyConst.errCodeType.FLAG_LOCATE_POSITION             -> {
                    if (isPressLocate.value) {
                        pushForward()
                    }
                    isPushLocateReply.value = true
                    aapsLogger.debug("isPushLocateReply is ${isPushLocateReply.value}")
                }
            }
        }
        else if ((it shr 8) == 0x81) {
            if(AlarmState.isHighAlarm(it)) {
                finish()
            }
        }
    }

    init {
        isBleConnect.value = aaruyBle.isConnected
        currentStatus.intValue = aaruyPump.pumpStateFlow.value.state
        aaruyPump.errCode.observeForever(errObserver)
        isPushLocateReply.value = false
        viewModelScope.launch {
            aaruyPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel connectionStateFlow: $state")
                if (state == ConnectionState.CONNECTED) {
                    isBleConnect.value = true
                }
                else {
                    isBleConnect.value = false
                }
            }
        }
        viewModelScope.launch {
            aaruyPump.pumpStateFlow.collect { state ->
                currentStatus.intValue = state.state
                when (state) {
                    AaruyPumpState.NO_RUNNING -> {

                    }
                    AaruyPumpState.BASALRATE_RUNNING -> {
                        if (_uiState.value.step == AaruyReplaceWizardStep.REPLACE_STEP_FILLING) {
                            finish()
                        }
                    }
                    AaruyPumpState.TMPBASALRATE_RUNNING -> {

                    }
                    AaruyPumpState.LARGEDOSE_RUNNING -> {

                    }
                    AaruyPumpState.EXHAUSTING -> {
                        isRemoveAir.value = true
                        aapsLogger.debug(LTag.PUMP, "isRemoveAir.value is ${isRemoveAir.value}")
                    }
                    AaruyPumpState.PUSHBACK_RUNNING -> {
                        if (!isRewinding.value) {
                            isRewinding.value = true
                        }
                    }
                    AaruyPumpState.PUSHFORWARD_RUNNING -> {

                    }
                    AaruyPumpState.PAUSE_RUNNING -> {

                    }
                    AaruyPumpState.STOP_RUNNING -> {
                        if (isRewinding.value) {
                            isRewindFinish.value = true
                            if (_uiState.value.step == AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK) {
                                aaruyBle.requestHistoryData(aaruyPump.lastHistoryTime)
                            }
                            isRewinding.value = false
                        }
                        if (isRemoveAir.value) {
                            isRemoveAir.value = false
                            startBaseInject()
                        }
                    }
                    AaruyPumpState.FIRST_INIT -> {

                    }
                }
            }

        }

        aaruyPump.errCode.observeForever() {

        }
    }

    override fun onCleared() {
        super.onCleared()
        aaruyPump.errCode.removeObserver(errObserver)
    }

    fun resetData() {
        isRunStatus6 = false
        isRemoveAir.value = false
        isPushLocateReply.value = false
        isRewinding.value = false
        isRewindFinish.value = false
        changeUiStatePage(AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK)
    }

    fun finishReplaceStep() {
        if (isRunStatus6 || isRemoveAir.value) {
            startBaseInject()
            isRunStatus6 = false
            isRemoveAir.value = false
        }
    }

    fun startPush6() {

        if (isRunStatus6)
            return

        CoroutineScope(Dispatchers.Default).launch {
            isRunStatus6 = true
            isRemoveAirFinish.value = true

            needlePushAir()

            delay(500.milliseconds)
            if (aaruyPump.pumpState.state == AaruyPumpState.NO_RUNNING.state) {
                startBaseInject()
            }
        }
    }

    fun replaceDevice() {
        scope.launch {
            if (aaruyService?.replaceDevice() == true) {
                aapsLogger.info(LTag.PUMP, "replaceDevice: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "replaceDevice: failure!")
            }
        }
    }

    fun pushForward() {
        scope.launch {
            if (aaruyService?.pushForward() == true) {
                aapsLogger.info(LTag.PUMP, "pushForward: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "pushForward: failure!")
            }
        }
    }

    fun sendNeedleType() {
        scope.launch {
            aaruyPump.softNeedleType = if (is6mm.value) 0 else 1
            aaruyService?.aaruyBle?.syncAndLoadBasicParam()
        }
    }

    fun needlePushAir() {
        scope.launch {
            if (aaruyService?.needlePushAir(aaruyPump.softNeedleType.toByte()) == true) {
                aapsLogger.info(LTag.PUMP, "needlePushAir: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "needlePushAir: failure!")
            }
        }
    }

    fun startBaseInject() {
        scope.launch {
            if (aaruyService?.startBaseInject() == true) {
                aapsLogger.info(LTag.PUMP, "startBaseInject: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startBaseInject: failure!")
            }
        }
    }

    fun finish() {
        _events.tryEmit(AaruyReplaceWizardEvent.RepalceFinish)
    }

    fun changeUiStatePage(step: AaruyReplaceWizardStep) {
        _uiState.update { it.copy(step = step) }
    }
}