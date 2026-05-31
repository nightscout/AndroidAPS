package app.aaps.implementation.queue.commands

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandCancelTempBasalTest : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync

    private fun newCommand(
        enforceNew: Boolean = true,
        autoForced: Boolean = false,
        callback: Callback? = null
    ) = CommandCancelTempBasal(
        aapsLogger, rh, activePlugin, pumpSync, dateUtil, pumpEnactResultProvider,
        enforceNew, autoForced, callback
    )

    @Test
    fun `execute returns pump's cancelTempBasal result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelTempBasal(true) } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand(enforceNew = true).execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `execute on autoForced does not sync-stop when no active TBR but flips result to success`() = runTest {
        // Pump cancellation fails, autoForced = true, but expectedPumpState has no TBR → skip sync
        // The autoForced branch still mutates the result to success=true, enacted=false.
        val pumpResult = PumpEnactResultObject(rh).success(false).enacted(false)
        val pump = mock<PumpWithConcentration> { on { cancelTempBasal(true) } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)
        val state = mock<PumpSync.PumpState>()
        whenever(state.temporaryBasal).thenReturn(null)
        whenever(pumpSync.expectedPumpState()).thenReturn(state)

        val result = newCommand(enforceNew = true, autoForced = true).execute()

        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(
            anyLong(), anyLong(), any(), any(), any()
        )
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `execute on autoForced sync-stops when pump-suspended TBR is active`() = runTest {
        // Pump cancellation fails, autoForced = true, expectedPumpState has TBR → sync-stop + flip to success.
        val pumpResult = PumpEnactResultObject(rh).success(false).enacted(false)
        val pump = mock<PumpWithConcentration> {
            on { cancelTempBasal(true) } doReturn pumpResult
            on { pumpDescription } doReturn PumpDescription().also { it.pumpType = PumpType.GENERIC_AAPS }
            on { serialNumber() } doReturn "serial-test"
        }
        whenever(activePlugin.activePump).thenReturn(pump)
        val state = mock<PumpSync.PumpState>()
        val activeTbr = mock<PumpSync.PumpState.TemporaryBasal>()
        whenever(state.temporaryBasal).thenReturn(activeTbr)
        whenever(pumpSync.expectedPumpState()).thenReturn(state)
        whenever(pumpSync.syncStopTemporaryBasalWithPumpId(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true)

        val result = newCommand(enforceNew = true, autoForced = true).execute()

        verify(pumpSync).syncStopTemporaryBasalWithPumpId(
            anyLong(), anyLong(), eq(PumpType.GENERIC_AAPS), eq("serial-test"), eq(true)
        )
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `execute non-autoForced does not flip a failed result`() = runTest {
        // autoForced = false → the result must NOT be mutated even on pump failure.
        val pumpResult = PumpEnactResultObject(rh).success(false).enacted(false).comment("pump rejected")
        val pump = mock<PumpWithConcentration> { on { cancelTempBasal(true) } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand(enforceNew = true, autoForced = false).execute()

        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(
            anyLong(), anyLong(), any(), any(), any()
        )
        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("pump rejected")
    }

    @Test
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelTempBasal(true) } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(enforceNew = true, callback = callback).executeWithCallback()

        assertThat(received).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `executeWithCallback with null callback does not crash`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelTempBasal(true) } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)

        newCommand(enforceNew = true, callback = null).executeWithCallback()
    }

    @Test
    fun `cancel invokes callback with success by default`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(callback = callback).cancel(app.aaps.core.ui.R.string.command_replaced)

        assertThat(received).isNotNull()
        assertThat(received!!.success).isTrue()
    }

    @Test
    fun `cancel invokes callback with failure when success=false`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(callback = callback).cancel(app.aaps.core.ui.R.string.command_replaced, success = false)

        assertThat(received).isNotNull()
        assertThat(received!!.success).isFalse()
    }

    @Test
    fun `cancel with null callback does not crash`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.connectiontimedout)).thenReturn("timeout")

        newCommand(callback = null).cancel(app.aaps.core.ui.R.string.connectiontimedout)
    }

    @Test
    fun `commandType is TEMPBASAL`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.TEMPBASAL)
    }

    @Test
    fun `log is CANCEL TEMPBASAL`() {
        assertThat(newCommand().log()).isEqualTo("CANCEL TEMPBASAL")
    }
}
