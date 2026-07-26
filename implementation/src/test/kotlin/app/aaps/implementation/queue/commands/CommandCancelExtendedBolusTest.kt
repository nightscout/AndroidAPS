package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommandCancelExtendedBolusTest : TestBaseWithProfile() {

    private fun newCommand(callback: Callback? = null) =
        CommandCancelExtendedBolus(aapsLogger, rh, activePlugin, pumpEnactResultProvider, callback)

    @Test
    fun `execute returns pump's cancelExtendedBolus result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelExtendedBolus() } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelExtendedBolus() } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(callback).executeWithCallback()

        assertThat(received).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `executeWithCallback with null callback does not crash`() = runTest {
        newCommand(callback = null).executeWithCallback()
    }

    @Test
    fun `cancel invokes callback with success by default`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(callback).cancel(app.aaps.core.ui.R.string.command_replaced)

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

        newCommand(callback).cancel(app.aaps.core.ui.R.string.command_replaced, success = false)

        assertThat(received).isNotNull()
        assertThat(received!!.success).isFalse()
    }

    @Test
    fun `cancel with null callback does not crash`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.connectiontimedout)).thenReturn("timeout")

        newCommand(callback = null).cancel(app.aaps.core.ui.R.string.connectiontimedout)
    }

    @Test
    fun `commandType is EXTENDEDBOLUS`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.EXTENDEDBOLUS)
    }

    @Test
    fun `log is CANCEL EXTENDEDBOLUS`() {
        assertThat(newCommand().log()).isEqualTo("CANCEL EXTENDEDBOLUS")
    }
}
