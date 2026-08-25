package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommandClearAlarmsTest : TestBaseWithProfile() {

    private fun newCommand(callback: Callback? = null) =
        CommandClearAlarms(aapsLogger, rh, activePlugin, pumpEnactResultProvider, callback)

    @Test
    fun `execute on Medtrum pump returns pump's clearAlarms result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val medtrumPump = mock<Pump>(extraInterfaces = arrayOf(Medtrum::class))
        whenever((medtrumPump as Medtrum).clearAlarms()).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(medtrumPump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    @Test
    fun `execute on non-Medtrum pump returns success not enacted`() = runTest {
        whenever(activePlugin.activePumpInternal).thenReturn(testPumpPlugin)

        val result = newCommand().execute()

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        whenever(activePlugin.activePumpInternal).thenReturn(testPumpPlugin)
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(callback).executeWithCallback()

        assertThat(received).isNotNull()
        assertThat(received!!.success).isTrue()
        assertThat(received.enacted).isFalse()
    }

    @Test
    fun `executeWithCallback with null callback does not crash`() = runTest {
        whenever(activePlugin.activePumpInternal).thenReturn(testPumpPlugin)

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
    fun `commandType is CLEAR_ALARMS`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.CLEAR_ALARMS)
    }

    @Test
    fun `log is CLEAR ALARMS`() {
        assertThat(newCommand().log()).isEqualTo("CLEAR ALARMS")
    }
}
