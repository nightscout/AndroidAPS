package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.Insight
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

class CommandStopPumpTest : TestBaseWithProfile() {

    private fun newCommand(callback: Callback? = null) =
        CommandStopPump(aapsLogger, rh, activePlugin, pumpEnactResultProvider, callback)

    @Test
    fun `execute on Insight pump returns pump's stopPump result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val insightPump = mock<Pump>(extraInterfaces = arrayOf(Insight::class))
        whenever((insightPump as Insight).stopPump()).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(insightPump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `execute on non-Insight pump returns success not enacted`() = runTest {
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
            override fun run() {
                received = result
            }
        }

        newCommand(callback).executeWithCallback()

        assertThat(received).isNotNull()
        assertThat(received!!.success).isTrue()
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
            override fun run() {
                received = result
            }
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
    fun `commandType is STOP_PUMP`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.STOP_PUMP)
    }

    @Test
    fun `log is STOP PUMP`() {
        assertThat(newCommand().log()).isEqualTo("STOP PUMP")
    }
}
