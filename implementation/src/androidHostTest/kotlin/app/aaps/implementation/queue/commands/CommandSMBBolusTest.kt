package app.aaps.implementation.queue.commands

import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.cancel
import app.aaps.core.keys.IntKey
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

class CommandSMBBolusTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var bolusProgressData: BolusProgressData

    private fun newCommand(info: DetailedBolusInfo) =
        CommandSMBBolus(
            aapsLogger, rh, dateUtil, activePlugin, persistenceLayer, preferences, bolusProgressData,
            pumpEnactResultProvider::invoke, info, BOLUS_GENERATION
        )

    private fun smbInfo(deliverAtTheLatest: Long = System.currentTimeMillis()) =
        DetailedBolusInfo().apply {
            insulin = 0.5
            bolusType = BS.Type.SMB
            this.deliverAtTheLatest = deliverAtTheLatest
        }

    @Test
    fun `execute rejects when within ApsMaxSmbFrequency interval`() = runTest {
        whenever(preferences.get(IntKey.ApsMaxSmbFrequency)).thenReturn(3)
        val recentBolus = BS(timestamp = dateUtil.now() - T.mins(1).msecs(), type = BS.Type.NORMAL, amount = 0.0, iCfg = someICfg)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(recentBolus)

        val result = newCommand(smbInfo()).execute()

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `execute calls pump when deliverAtTheLatest is recent enough`() = runTest {
        whenever(preferences.get(IntKey.ApsMaxSmbFrequency)).thenReturn(3)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(null)
        val info = smbInfo()
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { deliverTreatment(info) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)

        val result = newCommand(info).execute()

        assertThat(result).isSameInstanceAs(pumpResult)
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `execute rejects when SMB request is too old`() = runTest {
        whenever(preferences.get(IntKey.ApsMaxSmbFrequency)).thenReturn(3)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(null)
        // deliverAtTheLatest is in the past beyond the 1-minute grace window
        val stale = smbInfo(deliverAtTheLatest = System.currentTimeMillis() - T.mins(5).msecs())

        val result = newCommand(stale).execute()

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `execute rejects when deliverAtTheLatest is zero`() = runTest {
        whenever(preferences.get(IntKey.ApsMaxSmbFrequency)).thenReturn(3)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(null)
        val info = smbInfo(deliverAtTheLatest = 0L)

        val result = newCommand(info).execute()

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeAndComplete completes with execute result`() = runTest {
        whenever(preferences.get(IntKey.ApsMaxSmbFrequency)).thenReturn(3)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(null)
        val info = smbInfo()
        val pumpResult = PumpEnactResultObject(rh).success(true).enacted(true)
        val pump = mock<PumpWithConcentration> {
            on { deliverTreatment(info) } doReturn pumpResult
        }
        whenever(activePlugin.activePump).thenReturn(pump)
        val command = newCommand(info)

        command.executeAndComplete()

        val received = command.completion.await()
        assertThat(received).isSameInstanceAs(pumpResult)
    }

    @Test
    fun `cancel clears progress data and completes with success by default`() = runTest {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        val command = newCommand(smbInfo())

        command.cancel(app.aaps.core.ui.R.string.command_replaced)

        val received = command.completion.await()
        assertThat(received.success).isTrue()
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `cancel clears progress data and completes with failure when success=false`() = runTest {
        whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("replaced")
        val command = newCommand(smbInfo())

        command.cancel(app.aaps.core.ui.R.string.command_replaced, success = false)

        val received = command.completion.await()
        assertThat(received.success).isFalse()
        verify(bolusProgressData).clear(BOLUS_GENERATION)
    }

    @Test
    fun `commandType is SMB_BOLUS`() {
        assertThat(newCommand(smbInfo()).commandType).isEqualTo(Command.CommandType.SMB_BOLUS)
    }

    private companion object {

        const val BOLUS_GENERATION = 7L
    }
}
