package app.aaps.pump.aaruy.compose

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for [AaruyReplaceWizardViewModel]. Constructs the VM with mocked ctor deps and
 * asserts the initial [AaruyReplaceWizardUiState] plus the reactive behaviours driven by
 * `aaruyPump.connectionStateFlow`, `aaruyPump.pumpStateFlow` and `aaruyPump.errCode`.
 *
 * `errCode` is a `LiveData`; it is mocked and the observer passed to `observeForever` is captured
 * so tests can invoke it directly, bypassing LiveData's main-thread check without needing
 * `InstantTaskExecutorRule` (which is Rule-based and doesn't play nicely with JUnit 5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AaruyReplaceWizardViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var aaruyPlugin: AaruyPlugin
    @Mock private lateinit var aaruyPump: AaruyPump
    @Mock private lateinit var aaruyBle: AaruyBLE

    private val aaruyService: AaruyService = mock()
    private val errCode: MutableLiveData<Int> = mock()

    private val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val pumpStateFlow = MutableStateFlow(AaruyPumpState.NO_RUNNING)

    /** Captures the *first* observer passed to `errCode.observeForever` (the real one). */
    private var capturedErrObserver: Observer<Int>? = null

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        capturedErrObserver = null

        whenever(aaruyPlugin.getService()).thenReturn(aaruyService)
        whenever(aaruyPump.errCode).thenReturn(errCode)
        whenever(aaruyPump.connectionStateFlow).thenReturn(connectionStateFlow)
        whenever(aaruyPump.pumpStateFlow).thenReturn(pumpStateFlow)
        whenever(aaruyBle.isConnected).thenReturn(false)
        whenever(aaruyPump.lastHistoryTime).thenReturn(0L)
        whenever(aaruyService.aaruyBle).thenReturn(aaruyBle)

        // The VM calls observeForever twice: once with the real errObserver and once with an
        // empty lambda. Capture only the first.
        var captured = false
        whenever(errCode.observeForever(any())).thenAnswer {
            if (!captured) {
                capturedErrObserver = it.getArgument(0)
                captured = true
            }
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AaruyReplaceWizardViewModel(
        aapsLogger, aaruyPlugin, aaruyPump, aaruyBle
    )

    /** Feeds a low-byte (high byte == 0) err code through the captured observer. */
    private fun triggerErrCode(byte: Byte) {
        capturedErrObserver!!.onChanged(byte.toInt() and 0xFF)
    }

    // ============================================================
    // 1. 初始状态
    // ============================================================

    @Test
    fun `initial step is PUSH_BACK`() {
        val vm = createViewModel()
        assertThat(vm.uiState.value.step).isEqualTo(AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK)
    }

    @Test
    fun `initial steps exclude FILLING and POSITION`() {
        val vm = createViewModel()
        assertThat(vm.uiState.value.steps).containsExactly(
            AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK,
            AaruyReplaceWizardStep.REPLACE_STEP_CONNECT_BODY,
        ).inOrder()
    }

    @Test
    fun `initial isPushLocateReply is false`() {
        val vm = createViewModel()
        assertThat(vm.isPushLocateReply.value).isFalse()
    }

    @Test
    fun `initial isBleConnect reflects connectionStateFlow initial value`() {
        val vm = createViewModel()
        // connectionStateFlow starts at DISCONNECTED → collected synchronously by Unconfined
        assertThat(vm.isBleConnect.value).isFalse()
    }

    // ============================================================
    // 2. connectionStateFlow
    // ============================================================

    @Test
    fun `CONNECTED sets isBleConnect true`() {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.CONNECTED
        assertThat(vm.isBleConnect.value).isTrue()
    }

    @Test
    fun `DISCONNECTED sets isBleConnect false`() {
        whenever(aaruyBle.isConnected).thenReturn(true)
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.CONNECTED
        assertThat(vm.isBleConnect.value).isTrue()

        connectionStateFlow.value = ConnectionState.DISCONNECTED
        assertThat(vm.isBleConnect.value).isFalse()
    }

    // ============================================================
    // 3. pumpStateFlow 分支
    // ============================================================

    @Test
    fun `EXHAUSTING sets isRemoveAir true`() {
        val vm = createViewModel()
        pumpStateFlow.value = AaruyPumpState.EXHAUSTING
        assertThat(vm.isRemoveAir.value).isTrue()
    }

    @Test
    fun `PUSHBACK_RUNNING sets isRewinding true`() {
        val vm = createViewModel()
        pumpStateFlow.value = AaruyPumpState.PUSHBACK_RUNNING
        assertThat(vm.isRewinding.value).isTrue()
    }

    @Test
    fun `BASALRATE_RUNNING in FILLING step emits ReplaceFinish`() {
        val vm = createViewModel()
        vm.changeUiStatePage(AaruyReplaceWizardStep.REPLACE_STEP_FILLING)

        val events = mutableListOf<AaruyReplaceWizardEvent>()
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch {
            vm.events.collect { events.add(it) }
        }

        pumpStateFlow.value = AaruyPumpState.BASALRATE_RUNNING
        job.cancel()

        assertThat(events).contains(AaruyReplaceWizardEvent.RepalceFinish)
    }

    @Test
    fun `BASALRATE_RUNNING in other step does not emit`() {
        val vm = createViewModel()

        val events = mutableListOf<AaruyReplaceWizardEvent>()
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch {
            vm.events.collect { events.add(it) }
        }

        pumpStateFlow.value = AaruyPumpState.BASALRATE_RUNNING
        job.cancel()

        assertThat(events).isEmpty()
    }

    @Test
    fun `STOP_RUNNING after PUSHBACK clears isRewinding sets isRewindFinish and requests history`() {
        val vm = createViewModel()

        pumpStateFlow.value = AaruyPumpState.PUSHBACK_RUNNING
        assertThat(vm.isRewinding.value).isTrue()

        pumpStateFlow.value = AaruyPumpState.STOP_RUNNING
        assertThat(vm.isRewinding.value).isFalse()
        assertThat(vm.isRewindFinish.value).isTrue()
        verify(aaruyBle).requestHistoryData(any())
    }

    @Test
    fun `STOP_RUNNING after EXHAUSTING triggers startBaseInject`() {
        whenever(aaruyService.startBaseInject()).thenReturn(true)
        val vm = createViewModel()

        pumpStateFlow.value = AaruyPumpState.EXHAUSTING
        assertThat(vm.isRemoveAir.value).isTrue()

        pumpStateFlow.value = AaruyPumpState.STOP_RUNNING
        assertThat(vm.isRemoveAir.value).isFalse()
        // scope uses Dispatchers.Default — must wait
        verify(aaruyService, timeout(2_000)).startBaseInject()
    }

    // ============================================================
    // 4. errCode observer
    // ============================================================

    @Test
    fun `errCode FLAG_PUSH_BACK_STATUS sets isRewinding`() {
        val vm = createViewModel()
        triggerErrCode(AaruyConst.errCodeType.FLAG_PUSH_BACK_STATUS)
        assertThat(vm.isRewinding.value).isTrue()
    }

    @Test
    fun `errCode FLAG_LOCATE_POS_LIQUID_DISCHARGE clears isPressLocate`() {
        val vm = createViewModel()
        vm.isPressLocate.value = true
        triggerErrCode(AaruyConst.errCodeType.FLAG_LOCATE_POS_LIQUID_DISCHARGE)
        assertThat(vm.isPressLocate.value).isFalse()
    }

    @Test
    fun `errCode FLAG_LOCATE_POSITION sets isPushLocateReply`() {
        val vm = createViewModel()
        triggerErrCode(AaruyConst.errCodeType.FLAG_LOCATE_POSITION)
        assertThat(vm.isPushLocateReply.value).isTrue()
    }

    @Test
    fun `errCode FLAG_LOCATE_POSITION with isPressLocate calls pushForward`() {
        whenever(aaruyService.pushForward()).thenReturn(true)
        val vm = createViewModel()
        vm.isPressLocate.value = true

        triggerErrCode(AaruyConst.errCodeType.FLAG_LOCATE_POSITION)

        verify(aaruyService, timeout(2_000)).pushForward()
    }

    // ============================================================
    // 5. 行为方法
    // ============================================================

    @Test
    fun `resetData resets flags and moves step to PUSH_BACK`() {
        val vm = createViewModel()
        pumpStateFlow.value = AaruyPumpState.EXHAUSTING
        assertThat(vm.isRemoveAir.value).isTrue()

        vm.resetData()

        assertThat(vm.isRemoveAir.value).isFalse()
        assertThat(vm.isPushLocateReply.value).isFalse()
        assertThat(vm.isRewinding.value).isFalse()
        assertThat(vm.isRewindFinish.value).isFalse()
        assertThat(vm.uiState.value.step).isEqualTo(AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK)
    }

    @Test
    fun `changeUiStatePage updates step`() {
        val vm = createViewModel()
        vm.changeUiStatePage(AaruyReplaceWizardStep.REPLACE_STEP_CONNECT_BODY)
        assertThat(vm.uiState.value.step).isEqualTo(AaruyReplaceWizardStep.REPLACE_STEP_CONNECT_BODY)
    }

    @Test
    fun `finish emits ReplaceFinish event`() {
        val vm = createViewModel()
        val events = mutableListOf<AaruyReplaceWizardEvent>()
        val job = CoroutineScope(UnconfinedTestDispatcher()).launch {
            vm.events.collect { events.add(it) }
        }

        vm.finish()
        job.cancel()

        assertThat(events).contains(AaruyReplaceWizardEvent.RepalceFinish)
    }

    @Test
    fun `replaceDevice delegates to service`() {
        whenever(aaruyService.replaceDevice()).thenReturn(true)
        val vm = createViewModel()
        vm.replaceDevice()
        verify(aaruyService, timeout(2_000)).replaceDevice()
    }

    @Test
    fun `pushForward delegates to service`() {
        whenever(aaruyService.pushForward()).thenReturn(true)
        val vm = createViewModel()
        vm.pushForward()
        verify(aaruyService, timeout(2_000)).pushForward()
    }

    @Test
    fun `sendNeedleType uses 0 when is6mm is true`() {
        val vm = createViewModel()
        vm.is6mm.value = true
        vm.sendNeedleType()
        verify(aaruyPump, timeout(2_000)).softNeedleType = 0
        verify(aaruyBle, timeout(2_000)).syncAndLoadBasicParam()
    }

    @Test
    fun `sendNeedleType uses 1 when is6mm is false`() {
        val vm = createViewModel()
        vm.is6mm.value = false
        vm.sendNeedleType()
        verify(aaruyPump, timeout(2_000)).softNeedleType = 1
        verify(aaruyBle, timeout(2_000)).syncAndLoadBasicParam()
    }

    @Test
    fun `needlePushAir delegates with current softNeedleType`() {
        whenever(aaruyPump.softNeedleType).thenReturn(0)
        whenever(aaruyService.needlePushAir(any())).thenReturn(true)
        val vm = createViewModel()
        vm.needlePushAir()
        verify(aaruyService, timeout(2_000)).needlePushAir(0.toByte())
    }

    @Test
    fun `startBaseInject delegates to service`() {
        whenever(aaruyService.startBaseInject()).thenReturn(true)
        val vm = createViewModel()
        vm.startBaseInject()
        verify(aaruyService, timeout(2_000)).startBaseInject()
    }

    @Test
    fun `finishReplaceStep without pending operation does nothing`() {
        val vm = createViewModel()
        vm.finishReplaceStep()
        verify(aaruyService, never()).startBaseInject()
    }

    @Test
    fun `finishReplaceStep with isRemoveAir clears flag and calls startBaseInject`() {
        whenever(aaruyService.startBaseInject()).thenReturn(true)
        val vm = createViewModel()
        pumpStateFlow.value = AaruyPumpState.EXHAUSTING // isRemoveAir -> true

        vm.finishReplaceStep()

        assertThat(vm.isRemoveAir.value).isFalse()
        verify(aaruyService, timeout(2_000)).startBaseInject()
    }

    // ============================================================
    // 6. onCleared 清理
    // ============================================================

    @Test
    fun `onCleared removes errCode observer`() {
        val vm = createViewModel()

        // onCleared is protected — invoke via reflection
        val m = AaruyReplaceWizardViewModel::class.java.getDeclaredMethod("onCleared")
        m.isAccessible = true
        m.invoke(vm)

        verify(errCode).removeObserver(any())
    }
}