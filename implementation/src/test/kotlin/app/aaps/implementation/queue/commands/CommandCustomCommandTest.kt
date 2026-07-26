package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
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

    private fun newCommand(callback: Callback? = null) =
        CommandCustomCommand(aapsLogger, activePlugin, pumpEnactResultProvider, customCommand, callback)

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
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration>()
        whenever(pump.executeCustomCommand(customCommand)).thenReturn(pumpResult)
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
        val pump = mock<PumpWithConcentration>()
        whenever(pump.executeCustomCommand(customCommand)).thenReturn(null)
        whenever(activePlugin.activePump).thenReturn(pump)

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
