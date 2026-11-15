package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.PumpSync
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

class CommandTempBasalAbsoluteTest : TestBaseWithProfile() {

    private fun newCommand(
        absoluteRate: Double = 1.5,
        durationInMinutes: Int = 30,
        enforceNew: Boolean = true,
        tbrType: PumpSync.TemporaryBasalType = PumpSync.TemporaryBasalType.NORMAL
    ) = CommandTempBasalAbsolute(
        aapsLogger, rh, activePlugin, pumpEnactResultProvider::invoke,
        absoluteRate, durationInMinutes, enforceNew, tbrType
    )

    @Test
    fun `execute returns pump's setTempBasalAbsolute result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setTempBasalAbsolute(1.5, 30, true, PumpSync.TemporaryBasalType.NORMAL) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand(1.5, 30, true, PumpSync.TemporaryBasalType.NORMAL).execute()

        assertThat(result).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `executeAndComplete completes with execute result`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { setTempBasalAbsolute(1.5, 30, true, PumpSync.TemporaryBasalType.NORMAL) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)
        val command = newCommand(1.5, 30, true, PumpSync.TemporaryBasalType.NORMAL)

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
    fun `commandType is TEMPBASAL`() {
        assertThat(newCommand().commandType).isEqualTo(Command.CommandType.TEMPBASAL)
    }

    @Test
    fun `log includes rate and duration`() {
        assertThat(newCommand(absoluteRate = 0.5, durationInMinutes = 45).log())
            .isEqualTo("TEMP BASAL 0.5 U/h 45 min")
    }
}
