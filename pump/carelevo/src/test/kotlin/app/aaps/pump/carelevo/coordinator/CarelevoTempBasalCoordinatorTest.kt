package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.SimpleResultResponse
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import com.google.common.truth.Truth.assertThat
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import javax.inject.Provider

/**
 * DIRECT unit tests of [CarelevoTempBasalCoordinator] — exercises the coordinator without going through
 * the plugin, so the percent path, the cancel recompute-mode persist, the pump-authoritative sync on a
 * failed local persist, and the exception→failed-result mapping are all hit at the source. Complements
 * (does not duplicate) the plugin-level `CarelevoPumpPluginTempBasalTest`.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoTempBasalCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var bleSession: CarelevoBleSession
    @Mock lateinit var startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase
    @Mock lateinit var cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase

    private val serial = "SN-CARELEVO"
    private lateinit var sut: CarelevoTempBasalCoordinator

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(1_700_000_000_000L)
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn("AA:BB:CC:DD:EE:FF")
        whenever(startTempBasalInfusionUseCase.persistTempBasalStarted(any())).thenReturn(true)
        whenever(cancelTempBasalInfusionUseCase.persistTempBasalCancelled()).thenReturn(true)
        // Default happy-path session replies; individual tests override for reject/exception branches.
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }.thenReturn(SimpleResultResponse(0))
        whenever { bleSession.runSingle(any(), isA<TempBasalCancelCommand>(), any()) }.thenReturn(SimpleResultResponse(0))

        val pumpEnactResultProvider = Provider<PumpEnactResult> { FakePumpEnactResult() }
        sut = CarelevoTempBasalCoordinator(
            aapsLogger = aapsLogger,
            dateUtil = dateUtil,
            pumpSync = pumpSync,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            startTempBasalInfusionUseCase = startTempBasalInfusionUseCase,
            cancelTempBasalInfusionUseCase = cancelTempBasalInfusionUseCase
        )
    }

    // region setTempBasalAbsolute

    @Test
    fun `setTempBasalAbsolute rejects a negative rate before touching the session`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.setTempBasalAbsolute(-0.1, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}
        }
    }

    @Test
    fun `setTempBasalAbsolute rejects a non-positive duration before touching the session`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.setTempBasalAbsolute(1.2, 0, PumpSync.TemporaryBasalType.NORMAL, serial) {}
        }
    }

    @Test
    fun `setTempBasalAbsolute returns not enacted and never dials when bluetooth is off`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        var lastDataUpdated = false

        val result = sut.setTempBasalAbsolute(1.2, 30, PumpSync.TemporaryBasalType.NORMAL, serial) { lastDataUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(lastDataUpdated).isFalse()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<TempBasalCommand>(), any()) }
        verifyBlocking(pumpSync, never()) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalAbsolute returns no patch address comment when the address is missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = sut.setTempBasalAbsolute(1.2, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("no patch address")
    }

    @Test
    fun `setTempBasalAbsolute success populates the result, persists and syncs`() {
        var lastDataUpdated = false

        val result = sut.setTempBasalAbsolute(1.2, 90, PumpSync.TemporaryBasalType.NORMAL, serial) { lastDataUpdated = true }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.duration).isEqualTo(90)
        assertThat(result.absolute).isWithin(0.001).of(1.2)
        assertThat(result.isPercent).isFalse()
        assertThat(result.isTempCancel).isFalse()
        assertThat(lastDataUpdated).isTrue()
        verify(startTempBasalInfusionUseCase).persistTempBasalStarted(StartTempBasalInfusionRequestModel(isUnit = true, speed = 1.2, minutes = 90))
        verifyBlocking(pumpSync) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalAbsolute records in pumpSync even when the local persist fails`() {
        // Pump is authoritative: on ACK the TBR IS running, so a failed persist must not keep it out of pumpSync.
        whenever(startTempBasalInfusionUseCase.persistTempBasalStarted(any())).thenReturn(false)

        val result = sut.setTempBasalAbsolute(1.2, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(pumpSync) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalAbsolute maps a pump reject to an internal error without persisting or syncing`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }.thenReturn(SimpleResultResponse(1))
        var lastDataUpdated = false

        val result = sut.setTempBasalAbsolute(1.2, 30, PumpSync.TemporaryBasalType.NORMAL, serial) { lastDataUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("Internal error")
        assertThat(lastDataUpdated).isFalse()
        verify(startTempBasalInfusionUseCase, never()).persistTempBasalStarted(any())
        verifyBlocking(pumpSync, never()) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalAbsolute maps a session exception to a failed result carrying the message`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("timeout") }

        val result = sut.setTempBasalAbsolute(1.2, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("timeout")
        verifyBlocking(pumpSync, never()) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalAbsolute falls back to error comment when the exception has no message`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }
            .thenAnswer { throw RuntimeException() }

        val result = sut.setTempBasalAbsolute(1.2, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("error")
    }

    // endregion

    // region setTempBasalPercent

    @Test
    fun `setTempBasalPercent rejects a negative percent before touching the session`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.setTempBasalPercent(-1, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}
        }
    }

    @Test
    fun `setTempBasalPercent rejects a non-positive duration before touching the session`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.setTempBasalPercent(150, 0, PumpSync.TemporaryBasalType.NORMAL, serial) {}
        }
    }

    @Test
    fun `setTempBasalPercent returns not enacted and never dials when bluetooth is off`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        var lastDataUpdated = false

        val result = sut.setTempBasalPercent(150, 30, PumpSync.TemporaryBasalType.NORMAL, serial) { lastDataUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(lastDataUpdated).isFalse()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<TempBasalCommand>(), any()) }
    }

    @Test
    fun `setTempBasalPercent returns no patch address comment when the address is missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = sut.setTempBasalPercent(150, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("no patch address")
    }

    @Test
    fun `setTempBasalPercent success populates the result, persists by percent and syncs`() {
        var lastDataUpdated = false

        val result = sut.setTempBasalPercent(150, 30, PumpSync.TemporaryBasalType.NORMAL, serial) { lastDataUpdated = true }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.duration).isEqualTo(30)
        assertThat(result.percent).isEqualTo(150)
        assertThat(result.isPercent).isTrue()
        assertThat(result.isTempCancel).isFalse()
        assertThat(lastDataUpdated).isTrue()
        verify(startTempBasalInfusionUseCase).persistTempBasalStarted(StartTempBasalInfusionRequestModel(isUnit = false, percent = 150, minutes = 30))
        verifyBlocking(pumpSync) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalPercent records in pumpSync even when the local persist fails`() {
        whenever(startTempBasalInfusionUseCase.persistTempBasalStarted(any())).thenReturn(false)

        val result = sut.setTempBasalPercent(150, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(pumpSync) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalPercent maps a pump reject to an internal error without persisting or syncing`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }.thenReturn(SimpleResultResponse(1))
        var lastDataUpdated = false

        val result = sut.setTempBasalPercent(150, 30, PumpSync.TemporaryBasalType.NORMAL, serial) { lastDataUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("Internal error")
        assertThat(lastDataUpdated).isFalse()
        verify(startTempBasalInfusionUseCase, never()).persistTempBasalStarted(any())
        verifyBlocking(pumpSync, never()) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalPercent falls back to error comment when the exception has no message`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }
            .thenAnswer { throw RuntimeException() }

        val result = sut.setTempBasalPercent(150, 30, PumpSync.TemporaryBasalType.NORMAL, serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("error")
    }

    // endregion

    // region cancelTempBasal

    @Test
    fun `cancelTempBasal returns not enacted and never dials when bluetooth is off`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        var lastDataUpdated = false

        val result = sut.cancelTempBasal(serial) { lastDataUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(lastDataUpdated).isFalse()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<TempBasalCancelCommand>(), any()) }
    }

    @Test
    fun `cancelTempBasal returns no patch address comment when the address is missing`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = sut.cancelTempBasal(serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("no patch address")
    }

    @Test
    fun `cancelTempBasal success recompute-mode persists and syncs the stop`() {
        var lastDataUpdated = false

        val result = sut.cancelTempBasal(serial) { lastDataUpdated = true }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
        assertThat(lastDataUpdated).isTrue()
        verify(cancelTempBasalInfusionUseCase).persistTempBasalCancelled()
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelTempBasal records the stop even when the local persist fails`() {
        whenever(cancelTempBasalInfusionUseCase.persistTempBasalCancelled()).thenReturn(false)

        val result = sut.cancelTempBasal(serial) {}

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelTempBasal maps a pump reject to a failed result without persisting or syncing`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCancelCommand>(), any()) }.thenReturn(SimpleResultResponse(1))
        var lastDataUpdated = false

        val result = sut.cancelTempBasal(serial) { lastDataUpdated = true }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(lastDataUpdated).isFalse()
        verify(cancelTempBasalInfusionUseCase, never()).persistTempBasalCancelled()
        verifyBlocking(pumpSync, never()) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelTempBasal maps a session exception to a failed result`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCancelCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("timeout") }

        val result = sut.cancelTempBasal(serial) {}

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verifyBlocking(pumpSync, never()) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    // endregion

    /** Concrete [PumpEnactResult] so the builder chain and field mutations are observable in assertions. */
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
}
