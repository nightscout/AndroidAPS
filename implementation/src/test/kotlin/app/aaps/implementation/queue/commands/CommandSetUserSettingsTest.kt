package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.Diaconn
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

class CommandSetUserSettingsTest : TestBaseWithProfile() {

    private fun newCommand(callback: Callback? = null) =
        CommandSetUserSettings(aapsLogger, rh, activePlugin, pumpEnactResultProvider, callback)

    @Test
    fun `execute on Dana pump returns pump's setUserOptions result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val danaPump = mock<Pump>(extraInterfaces = arrayOf(Dana::class))
        whenever((danaPump as Dana).setUserOptions()).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(danaPump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `execute on Diaconn pump returns pump's setUserOptions result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val diaconnPump = mock<Pump>(extraInterfaces = arrayOf(Diaconn::class))
        whenever((diaconnPump as Diaconn).setUserOptions()).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(diaconnPump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `execute on Medtrum pump returns pump's setUserOptions result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val medtrumPump = mock<Pump>(extraInterfaces = arrayOf(Medtrum::class))
        whenever((medtrumPump as Medtrum).setUserOptions()).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(medtrumPump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `execute on unrelated pump returns success not enacted`() = runTest {
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
    fun `commandType is SET_USER_SETTINGS`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.SET_USER_SETTINGS)
    }

    @Test
    fun `log is SET USER SETTINGS`() {
        assertThat(newCommand().log()).isEqualTo("SET USER SETTINGS")
    }
}
