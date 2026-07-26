package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandBolusTest : TestBaseWithProfile() {

    @Mock lateinit var bolusProgressData: BolusProgressData

    private val info = DetailedBolusInfo().apply { insulin = 1.0 }

    private fun newCommand(type: Command.CommandType = Command.CommandType.BOLUS, callback: Callback? = null) =
        CommandBolus(
            aapsLogger, rh, activePlugin, pumpEnactResultProvider, bolusProgressData,
            info, callback, type, BOLUS_GENERATION
        )

    @Test
    fun `execute returns pump result and marks complete on success`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { deliverTreatment(info) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        verify(bolusProgressData).completeAndAutoClear(BOLUS_GENERATION)
    }

    @Test
    fun `execute clears progress data on failure`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(false).enacted(false)
        val pump = mock<PumpWithConcentration> {
            on { deliverTreatment(info) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand().execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `executeWithCallback forwards execute result to callback`() = runTest {
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { deliverTreatment(info) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                received = result
            }
        }

        newCommand(callback = callback).executeWithCallback()

        assertThat(received).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `cancel clears progress data and invokes callback with success by default`() {
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
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `cancel clears progress data and invokes callback with failure when success=false`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        var received: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                received = result
            }
        }

        newCommand(callback = callback).cancel(app.aaps.core.ui.R.string.command_replaced, success = false)

        assertThat(received).isNotNull()
        assertThat(received!!.success).isFalse()
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `commandType is what constructor was given`() {
        assertThat(newCommand(type = Command.CommandType.BOLUS).commandType).isEqualTo(Command.CommandType.BOLUS)
        assertThat(newCommand(type = Command.CommandType.SMB_BOLUS).commandType).isEqualTo(Command.CommandType.SMB_BOLUS)
    }

    private companion object {

        const val BOLUS_GENERATION = 7L
    }
}
