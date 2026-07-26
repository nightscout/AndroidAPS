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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandInsightSetTBROverNotificationTest : TestBaseWithProfile() {

    private fun newCommand(enabled: Boolean = true, callback: Callback? = null) =
        CommandInsightSetTBROverNotification(aapsLogger, rh, activePlugin, pumpEnactResultProvider, enabled, callback)

    @Test
    fun `execute on Insight pump returns pump's setTBROverNotification result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val insightPump = mock<Pump>(extraInterfaces = arrayOf(Insight::class))
        whenever((insightPump as Insight).setTBROverNotification(true)).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(insightPump)

        val result = newCommand(enabled = true).execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        verify(insightPump).setTBROverNotification(true)
    }

    @Test
    fun `execute passes the enabled flag through to the pump`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val insightPump = mock<Pump>(extraInterfaces = arrayOf(Insight::class))
        whenever((insightPump as Insight).setTBROverNotification(false)).thenReturn(pumpResult)
        whenever(activePlugin.activePumpInternal).thenReturn(insightPump)

        newCommand(enabled = false).execute()

        verify(insightPump).setTBROverNotification(false)
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
            override fun run() { received = result }
        }

        newCommand(callback = callback).executeWithCallback()

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
    fun `commandType is INSIGHT_SET_TBR_OVER_ALARM`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.INSIGHT_SET_TBR_OVER_ALARM)
    }
}
