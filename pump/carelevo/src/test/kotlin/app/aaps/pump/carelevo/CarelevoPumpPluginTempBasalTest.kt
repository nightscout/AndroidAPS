package app.aaps.pump.carelevo

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.carelevo.ble.commands.SimpleResultResponse
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.isA
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

class CarelevoPumpPluginTempBasalTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `setTempBasalAbsolute should return not enacted when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        val result = runBlocking { plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalAbsolute should return not enacted when no patch address is stored`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = runBlocking { plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalAbsolute should succeed on success response`() {
        val result = runBlocking { plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.absolute).isWithin(0.001).of(1.2)
    }

    @Test
    fun `setTempBasalAbsolute should fail when the pump rejects the command`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }
            .thenReturn(SimpleResultResponse(1))

        val result = runBlocking { plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalAbsolute should require a positive duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { plugin.setTempBasalAbsolute(1.2, 0, false, PumpSync.TemporaryBasalType.NORMAL) }
        }
    }

    @Test
    fun `setTempBasalAbsolute should record in pumpSync even when the local persist fails`() {
        // Pump ACKed — the TBR IS running on the patch; a failed local persist must not keep it
        // out of pumpSync or basal IOB modeling diverges for the whole TBR duration.
        whenever(startTempBasalInfusionUseCase.persistTempBasalStarted(any())).thenReturn(false)

        val result = runBlocking { plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(pumpSync) { syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelTempBasal should record the stop in pumpSync even when the local persist fails`() {
        whenever(cancelTempBasalInfusionUseCase.persistTempBasalCancelled()).thenReturn(false)

        val result = runBlocking { plugin.cancelTempBasal(false) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setTempBasalPercent should succeed on success response`() {
        val result = runBlocking { plugin.setTempBasalPercent(150, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.percent).isEqualTo(150)
    }

    @Test
    fun `setTempBasalPercent should fail when the session throws`() {
        // A BLE error must be caught and mapped to a failed PumpEnactResult, not propagated out of the
        // plugin — an exception escaping here would crash the command queue.
        whenever { bleSession.runSingle(any(), isA<TempBasalCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("timeout") }

        val result = runBlocking { plugin.setTempBasalPercent(150, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelTempBasal should return not enacted when no patch address is stored`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = runBlocking { plugin.cancelTempBasal(false) }

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelTempBasal should succeed on success response`() {
        val result = runBlocking { plugin.cancelTempBasal(false) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
    }

    @Test
    fun `cancelTempBasal should return success false and enacted false on timeout`() {
        whenever { bleSession.runSingle(any(), isA<TempBasalCancelCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("timeout") }

        val result = runBlocking { plugin.cancelTempBasal(false) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }
}
