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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandInsightSetTBROverNotificationTest : TestBaseWithProfile() {

    private fun newCommand(enabled: Boolean = true) =
        CommandInsightSetTBROverNotification(aapsLogger, rh, activePlugin, pumpEnactResultProvider::invoke, enabled)

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
    fun `commandType is INSIGHT_SET_TBR_OVER_ALARM`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.INSIGHT_SET_TBR_OVER_ALARM)
    }
}
