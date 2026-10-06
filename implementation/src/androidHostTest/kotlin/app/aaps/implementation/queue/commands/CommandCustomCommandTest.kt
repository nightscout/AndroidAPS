package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.queue.cancel
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommandCustomCommandTest : TestBaseWithProfile() {

    private val customCommand = object : CustomCommand {
        override val statusDescription: String = "TEST_CUSTOM"
    }

    private fun newCommand() =
        CommandCustomCommand(aapsLogger, activePlugin, pumpEnactResultProvider::invoke, customCommand)

    @Test
    fun `execute returns pump's executeCustomCommand result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration>()
        whenever(pump.executeCustomCommand(customCommand)).thenReturn(pumpResult)
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    @Test
    fun `execute returns success not enacted when pump returns null`() = runTest {
        val pump = mock<PumpWithConcentration>()
        whenever(pump.executeCustomCommand(customCommand)).thenReturn(null)
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand().execute()

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `executeAndComplete completes with execute result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration>()
        whenever(pump.executeCustomCommand(customCommand)).thenReturn(pumpResult)
        whenever(activePlugin.activePump).thenReturn(pump)
        val command = newCommand()

        command.executeAndComplete()

        val received = command.completion.await()
        assertThat(received).isSameInstanceAs(pumpResult)
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
    fun `commandType is CUSTOM_COMMAND`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.CUSTOM_COMMAND)
    }

    @Test
    fun `status and log return customCommand statusDescription`() {
        val cmd = newCommand()
        assertThat(cmd.status()).isEqualTo("TEST_CUSTOM")
        assertThat(cmd.log()).isEqualTo("TEST_CUSTOM")
    }
}
