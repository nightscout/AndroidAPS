package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.BolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.BolusCancelResponse
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelResponse
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusResponse
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.isA
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

/**
 * Direct unit tests for [CarelevoBolusCoordinator] targeting the branches NOT exercised through
 * `CarelevoPumpPluginBolusTest`: the immediate-bolus out-of-band cancel (whole path + timeout-only
 * retry), the extended-bolus guard/failure branches, and the pure-extended `immediateDose == 0`
 * wire encoding.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoBolusCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var bleSession: CarelevoBleSession
    @Mock lateinit var startImmeBolusInfusionUseCase: CarelevoStartImmeBolusInfusionUseCase
    @Mock lateinit var finishImmeBolusInfusionUseCase: CarelevoFinishImmeBolusInfusionUseCase
    @Mock lateinit var cancelImmeBolusInfusionUseCase: CarelevoCancelImmeBolusInfusionUseCase
    @Mock lateinit var startExtendBolusInfusionUseCase: CarelevoStartExtendBolusInfusionUseCase
    @Mock lateinit var cancelExtendBolusInfusionUseCase: CarelevoCancelExtendBolusInfusionUseCase

    private lateinit var sut: CarelevoBolusCoordinator

    private val serial = "CARELEVO-SN"
    private val address = "AA:BB:CC:DD:EE:FF"

    /** Concrete [PumpEnactResult] the provider hands out (mirrors the test-base fake). */
    private class FakePumpEnactResult : PumpEnactResult {

        override var success: Boolean = false
        override var enacted: Boolean = false
        override var comment: String = ""
        override var duration: Int = -1
        override var absolute: Double = -1.0
        override var percent: Int = -1
        override var isPercent: Boolean = false
        override var isTempCancel: Boolean = false
        override var bolusDelivered: Double = 0.0
        override var queued: Boolean = false

        override fun success(success: Boolean): PumpEnactResult = apply { this.success = success }
        override fun enacted(enacted: Boolean): PumpEnactResult = apply { this.enacted = enacted }
        override fun comment(comment: String): PumpEnactResult = apply { this.comment = comment }
        override fun comment(comment: Int): PumpEnactResult = apply { this.comment = comment.toString() }
        override fun duration(duration: Int): PumpEnactResult = apply { this.duration = duration }
        override fun absolute(absolute: Double): PumpEnactResult = apply { this.absolute = absolute }
        override fun percent(percent: Int): PumpEnactResult = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean): PumpEnactResult = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean): PumpEnactResult = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double): PumpEnactResult = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean): PumpEnactResult = apply { this.queued = queued }
    }

    /** Throws a genuine [kotlinx.coroutines.TimeoutCancellationException] (no public ctor otherwise). */
    private fun answerTimeout(): Any = runBlocking { withTimeout(1) { delay(10_000) } }

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(1_000_000L)
        whenever(rh.gs(any<Int>())).thenReturn("Mocked")
        whenever(rh.gs(any<Int>(), any())).thenReturn("Mocked")

        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(address)

        // Progress state is read (`.value?.percent`) on the immediate-cancel success path.
        whenever(bolusProgressData.state).thenReturn(MutableStateFlow<BolusProgressState?>(null))

        // Happy-path session defaults; individual tests override per command type.
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCommand>(), any()) }
            .thenReturn(ExtendBolusResponse(resultCode = 0, expectedTimeSeconds = 60))
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCancelCommand>(), any()) }
            .thenReturn(ExtendBolusCancelResponse(resultCode = 0, infusedAmount = 0.5))
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }
            .thenReturn(BolusCancelResponse(resultCode = 0, infusedAmount = 0.3))

        whenever(startExtendBolusInfusionUseCase.persistExtendBolusStarted(any(), any(), any())).thenReturn(true)
        whenever(cancelExtendBolusInfusionUseCase.persistExtendBolusCancelled()).thenReturn(true)
        whenever(cancelImmeBolusInfusionUseCase.persistImmeBolusCancelled()).thenReturn(true)

        val provider = Provider<PumpEnactResult> { FakePumpEnactResult() }
        sut = CarelevoBolusCoordinator(
            aapsLogger = aapsLogger,
            rh = rh,
            dateUtil = dateUtil,
            bolusProgressData = bolusProgressData,
            pumpSync = pumpSync,
            aapsSchedulers = aapsSchedulers,
            pumpEnactResultProvider = provider,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            startImmeBolusInfusionUseCase = startImmeBolusInfusionUseCase,
            finishImmeBolusInfusionUseCase = finishImmeBolusInfusionUseCase,
            cancelImmeBolusInfusionUseCase = cancelImmeBolusInfusionUseCase,
            startExtendBolusInfusionUseCase = startExtendBolusInfusionUseCase,
            cancelExtendBolusInfusionUseCase = cancelExtendBolusInfusionUseCase
        )
    }

    /** Reflectively seeds the private `bolusExpectMs` so the cancel-retry budget is > 0. */
    private fun seedBolusExpectMs(value: Long) {
        val field = CarelevoBolusCoordinator::class.java.getDeclaredField("bolusExpectMs")
        field.isAccessible = true
        field.setLong(sut, value)
    }

    // ---------------------------------------------------------------------------------------------
    // setExtendedBolus — guards + failure + pure-extended encoding
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `setExtendedBolus rejects a non-positive insulin`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.setExtendedBolus(insulin = 0.0, durationInMinutes = 30, serialNumber = serial)
        }
    }

    @Test
    fun `setExtendedBolus rejects a non-positive duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.setExtendedBolus(insulin = 1.0, durationInMinutes = 0, serialNumber = serial)
        }
    }

    @Test
    fun `setExtendedBolus returns a not-enacted result when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        val result = sut.setExtendedBolus(insulin = 1.0, durationInMinutes = 30, serialNumber = serial)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<ExtendBolusCommand>(), any()) }
    }

    @Test
    fun `setExtendedBolus fails with no patch address when the address is missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = sut.setExtendedBolus(insulin = 1.0, durationInMinutes = 30, serialNumber = serial)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("no patch address")
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<ExtendBolusCommand>(), any()) }
    }

    @Test
    fun `setExtendedBolus encodes a pure extended dose with immediateDose zero`() {
        var captured: ExtendBolusCommand? = null
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCommand>(), any()) }.thenAnswer { inv ->
            captured = inv.getArgument(1) as ExtendBolusCommand
            ExtendBolusResponse(resultCode = 0, expectedTimeSeconds = 60)
        }

        // insulin 3.0 U over 90 min → speed 3.0 / (90/60) = 2.0 U/h, hour=1, min=30.
        val result = sut.setExtendedBolus(insulin = 3.0, durationInMinutes = 90, serialNumber = serial)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        val bytes = captured!!.encode()
        assertThat(bytes[0]).isEqualTo(0x25.toByte())      // opcode
        assertThat(bytes[1]).isEqualTo(0.toByte())         // immediateDose whole == 0
        assertThat(bytes[2]).isEqualTo(0.toByte())         // immediateDose centi == 0
        assertThat(bytes[3]).isEqualTo(2.toByte())         // extendedSpeed whole == 2
        assertThat(bytes[4]).isEqualTo(0.toByte())         // extendedSpeed centi == 0
        assertThat(bytes[5]).isEqualTo(1.toByte())         // hour
        assertThat(bytes[6]).isEqualTo(30.toByte())        // min
    }

    @Test
    fun `setExtendedBolus fails when the session throws`() {
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("boom") }

        val result = sut.setExtendedBolus(insulin = 1.0, durationInMinutes = 30, serialNumber = serial)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(pumpSync, never()) { syncExtendedBolusWithPumpId(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ---------------------------------------------------------------------------------------------
    // cancelExtendedBolus — guards + reject + success
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `cancelExtendedBolus returns a not-enacted result when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        val result = sut.cancelExtendedBolus(serialNumber = serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<ExtendBolusCancelCommand>(), any()) }
    }

    @Test
    fun `cancelExtendedBolus fails with no patch address when the address is missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = sut.cancelExtendedBolus(serialNumber = serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("no patch address")
    }

    @Test
    fun `cancelExtendedBolus fails when the pump rejects the command`() {
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCancelCommand>(), any()) }
            .thenReturn(ExtendBolusCancelResponse(resultCode = 1, infusedAmount = 0.0))
        var lastUpdated = false

        val result = sut.cancelExtendedBolus(serialNumber = serial) { lastUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(lastUpdated).isFalse()
        verifyBlocking(pumpSync, never()) { syncStopExtendedBolusWithPumpId(any(), any(), any(), any()) }
    }

    @Test
    fun `cancelExtendedBolus success invokes onLastDataUpdated, records the stop and flags temp-cancel`() {
        var lastUpdated = false

        val result = sut.cancelExtendedBolus(serialNumber = serial) { lastUpdated = true }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
        assertThat(lastUpdated).isTrue()
        verifyBlocking(pumpSync) { syncStopExtendedBolusWithPumpId(any(), any(), any(), any()) }
    }

    // ---------------------------------------------------------------------------------------------
    // cancelImmediateBolus — whole out-of-band cancel path + timeout-only retry
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `cancelImmediateBolus does nothing when there is no patch address`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        var lastUpdated = false

        sut.cancelImmediateBolus(serialNumber = serial) { lastUpdated = true }

        assertThat(lastUpdated).isFalse()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<BolusCancelCommand>(), any()) }
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelImmediateBolus success records the infused amount and syncs the bolus`() {
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }
            .thenReturn(BolusCancelResponse(resultCode = 0, infusedAmount = 0.42))
        var lastUpdated = false

        sut.cancelImmediateBolus(serialNumber = serial) { lastUpdated = true }

        assertThat(lastUpdated).isTrue()
        verifyBlocking(cancelImmeBolusInfusionUseCase) { persistImmeBolusCancelled() }
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelImmediateBolus records the bolus in pumpSync even when the local persist fails`() {
        whenever(cancelImmeBolusInfusionUseCase.persistImmeBolusCancelled()).thenReturn(false)
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }
            .thenReturn(BolusCancelResponse(resultCode = 0, infusedAmount = 0.42))

        sut.cancelImmediateBolus(serialNumber = serial) {}

        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelImmediateBolus does not sync when the pump rejects the cancel`() {
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }
            .thenReturn(BolusCancelResponse(resultCode = 1, infusedAmount = 0.0))
        var lastUpdated = false

        sut.cancelImmediateBolus(serialNumber = serial) { lastUpdated = true }

        assertThat(lastUpdated).isFalse()
        verifyBlocking(cancelImmeBolusInfusionUseCase, never()) { persistImmeBolusCancelled() }
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
        verifyBlocking(bleSession, times(1)) { runSingle(any(), isA<BolusCancelCommand>(), any()) }
    }

    @Test
    fun `cancelImmediateBolus gives up without retry on a non-timeout error`() {
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("connect refused") }

        sut.cancelImmediateBolus(serialNumber = serial) {}

        verifyBlocking(bleSession, times(1)) { runSingle(any(), isA<BolusCancelCommand>(), any()) }
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelImmediateBolus does not retry a timeout when no retries are budgeted`() {
        // bolusExpectMs defaults to 0 → calculateMaxRetry yields 0 → a single attempt only.
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }.thenAnswer { answerTimeout() }

        sut.cancelImmediateBolus(serialNumber = serial) {}

        verifyBlocking(bleSession, times(1)) { runSingle(any(), isA<BolusCancelCommand>(), any()) }
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelImmediateBolus retries a timed-out attempt then gives up after exhausting the budget`() {
        seedBolusExpectMs(30_000L) // → maxRetry capped to 1 → two attempts total
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }.thenAnswer { answerTimeout() }

        sut.cancelImmediateBolus(serialNumber = serial) {}

        verifyBlocking(bleSession, times(2)) { runSingle(any(), isA<BolusCancelCommand>(), any()) }
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelImmediateBolus retries a timed-out attempt and succeeds on the second try`() {
        seedBolusExpectMs(30_000L) // → maxRetry capped to 1 → a retry is allowed
        val calls = AtomicInteger(0)
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }.thenAnswer {
            if (calls.getAndIncrement() == 0) answerTimeout()
            else BolusCancelResponse(resultCode = 0, infusedAmount = 0.4)
        }
        var lastUpdated = false

        sut.cancelImmediateBolus(serialNumber = serial) { lastUpdated = true }

        assertThat(lastUpdated).isTrue()
        verifyBlocking(bleSession, times(2)) { runSingle(any(), isA<BolusCancelCommand>(), any()) }
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }
}
