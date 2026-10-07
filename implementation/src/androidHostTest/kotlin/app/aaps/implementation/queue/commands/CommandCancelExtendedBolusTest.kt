package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.cancel
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommandCancelExtendedBolusTest : TestBaseWithProfile() {

    private fun newCommand() =
        CommandCancelExtendedBolus(aapsLogger, rh, activePlugin, pumpEnactResultProvider::invoke)

    @Test
    fun `execute returns pump's cancelExtendedBolus result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelExtendedBolus() } doReturn pumpResult }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `executeAndComplete completes with execute result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> { on { cancelExtendedBolus() } doReturn pumpResult }
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
    fun `commandType is EXTENDEDBOLUS`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.EXTENDEDBOLUS)
    }

    @Test
    fun `log is CANCEL EXTENDEDBOLUS`() {
        assertThat(newCommand().log()).isEqualTo("CANCEL EXTENDEDBOLUS")
    }
}
