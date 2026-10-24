package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.Medtrum
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

class CommandDeactivateTest : TestBaseWithProfile() {

    private fun newCommand() =
        CommandDeactivate(aapsLogger, rh, activePlugin, pumpEnactResultProvider::invoke)

    @Test
    fun `execute on Medtrum pump returns pump's deactivate result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val medtrumPump = mock<Pump>(extraInterfaces = arrayOf(Medtrum::class))
        whenever((medtrumPump as Medtrum).deactivate()).thenReturn(pumpResult)
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
    fun `executeAndComplete completes with execute result`() = runTest {
        whenever(activePlugin.activePumpInternal).thenReturn(testPumpPlugin)
        val command = newCommand()

        command.executeAndComplete()

        val received = command.completion.await()
        assertThat(received.success).isTrue()
        assertThat(received.enacted).isFalse()
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
    fun `commandType is DEACTIVATE`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.DEACTIVATE)
    }

    @Test
    fun `log is DEACTIVATE`() {
        assertThat(newCommand().log()).isEqualTo("DEACTIVATE")
    }
}
