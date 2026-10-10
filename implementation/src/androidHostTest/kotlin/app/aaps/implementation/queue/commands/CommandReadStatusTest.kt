package app.aaps.implementation.queue.commands

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.cancel
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandReadStatusTest : TestBaseWithProfile() {

    @Mock lateinit var localAlertUtils: LocalAlertUtils

    private fun newCommand(reason: String = "test reason") =
        CommandReadStatus(aapsLogger, rh, activePlugin, localAlertUtils, pumpEnactResultProvider::invoke, reason)

    private fun pumpWithLastData(lastDataTime: Long): PumpWithConcentration {
        val pump = mock<PumpWithConcentration>()
        whenever(pump.lastDataTime).thenReturn(MutableStateFlow(lastDataTime))
        whenever(activePlugin.activePump).thenReturn(pump)
        return pump
    }

    @Test
    fun `execute returns success=true when lastConnection is recent`() = runTest {
        val recent = System.currentTimeMillis() - T.secs(10).msecs()
        pumpWithLastData(recent)

        val result = newCommand().execute()

        assertThat(result.success).isTrue()
    }

    @Test
    fun `execute returns success=false when lastConnection is stale`() = runTest {
        val stale = System.currentTimeMillis() - T.mins(5).msecs()
        pumpWithLastData(stale)

        val result = newCommand().execute()

        assertThat(result.success).isFalse()
    }

    @Test
    fun `execute calls pump getPumpStatus with the reason`() = runTest {
        val pump = pumpWithLastData(System.currentTimeMillis())

        newCommand(reason = "wake-up").execute()

        verify(pump).getPumpStatus("wake-up")
    }

    @Test
    fun `execute reports pump status read to LocalAlertUtils`() = runTest {
        pumpWithLastData(System.currentTimeMillis())

        newCommand().execute()

        verify(localAlertUtils).reportPumpStatusRead()
    }

    @Test
    fun `executeAndComplete completes with execute result`() = runTest {
        pumpWithLastData(System.currentTimeMillis() - T.secs(10).msecs())
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
    fun `commandType is READSTATUS`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.READSTATUS)
    }

    @Test
    fun `log includes reason`() {
        assertThat(newCommand(reason = "boot").log()).isEqualTo("READSTATUS boot")
    }
}
