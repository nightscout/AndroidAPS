package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.Insight
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.cancel
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommandStartPumpTest : TestBaseWithProfile() {

    private fun newCommand() =
        CommandStartPump(aapsLogger, rh, activePlugin, pumpEnactResultProvider::invoke)

    @Test
    fun `execute on Insight pump returns pump's startPump result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val insightPump = mock<Pump>(extraInterfaces = arrayOf(Insight::class))
        whenever((insightPump as Insight).startPump()).thenReturn(pumpResult)
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
    fun `executeAndComplete completes with execute result`() = runTest {
        whenever(activePlugin.activePumpInternal).thenReturn(testPumpPlugin)
        val command = newCommand()

        command.executeAndComplete()

        val received = command.completion.await()
        assertThat(received.success).isTrue()
    }

    @Test
    fun `cancel completes with success by default`() = runTest {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        val command = newCommand()

        command.cancel(app.aaps.core.ui.R.string.command_replaced)

        val received = command.completion.await()
        assertThat(received.success).isTrue()
    }

    @Test
    fun `cancel completes with failure when success=false`() = runTest {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        val command = newCommand()

        command.cancel(app.aaps.core.ui.R.string.command_replaced, success = false)

        val received = command.completion.await()
        assertThat(received.success).isFalse()
    }

    @Test
    fun `commandType is START_PUMP`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.START_PUMP)
    }

    @Test
    fun `log is START PUMP`() {
        assertThat(newCommand().log()).isEqualTo("START PUMP")
    }
}
