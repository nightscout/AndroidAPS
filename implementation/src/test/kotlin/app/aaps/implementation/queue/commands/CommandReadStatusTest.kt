package app.aaps.implementation.queue.commands

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
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

    private fun newCommand(reason: String = "test reason", callback: Callback? = null) =
        CommandReadStatus(aapsLogger, rh, activePlugin, localAlertUtils, pumpEnactResultProvider, reason, callback)

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
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        pumpWithLastData(System.currentTimeMillis() - T.secs(10).msecs())
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                received = result
            }
        }

        newCommand(callback = callback).executeWithCallback()

        assertThat(received).isNotNull()
        assertThat(received!!.success).isTrue()
    }

    @Test
    fun `executeWithCallback with null callback does not crash`() = runTest {
        pumpWithLastData(System.currentTimeMillis())

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
    fun `commandType is READSTATUS`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.READSTATUS)
    }

    @Test
    fun `log includes reason`() {
        assertThat(newCommand(reason = "boot").log()).isEqualTo("READSTATUS boot")
    }
}
