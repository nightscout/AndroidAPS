package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandTempBasalPercentTest : TestBaseWithProfile() {

    private fun newCommand(
        percent: Int = 80,
        durationInMinutes: Int = 30,
        enforceNew: Boolean = true,
        tbrType: PumpSync.TemporaryBasalType = PumpSync.TemporaryBasalType.NORMAL,
        callback: Callback? = null
    ) = CommandTempBasalPercent(
        aapsLogger, rh, activePlugin, pumpEnactResultProvider,
        percent, durationInMinutes, enforceNew, tbrType, callback
    )

    @Test
    fun `execute with non-100 percent calls setTempBasalPercent`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setTempBasalPercent(80, 30, true, PumpSync.TemporaryBasalType.NORMAL) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand(percent = 80).execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        verify(pump, never()).cancelTempBasal(true)
    }

    @Test
    fun `execute with 100 percent cancels TBR instead`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { cancelTempBasal(true) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand(percent = 100).execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        verify(pump, never()).setTempBasalPercent(any(), any(), any(), any())
    }

    @Test
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setTempBasalPercent(80, 30, true, PumpSync.TemporaryBasalType.NORMAL) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() { received = result }
        }

        newCommand(percent = 80, callback = callback).executeWithCallback()

        assertThat(received).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `executeWithCallback with null callback does not crash`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setTempBasalPercent(80, 30, true, PumpSync.TemporaryBasalType.NORMAL) } doReturn pumpResult
        }
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
    fun `commandType is TEMPBASAL`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.TEMPBASAL)
    }

    @Test
    fun `log includes percent and duration`() {
        assertThat(newCommand(percent = 75, durationInMinutes = 45).log()).isEqualTo("TEMP BASAL 75% 45 min")
    }
}
