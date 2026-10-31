package app.aaps.pump.carelevo

import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.pump.carelevo.command.CmdTimeZoneUpdate
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

class CarelevoPumpPluginStatusTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `connect should not throw and should keep plugin usable`() {
        plugin.connect("test")

        assertThat(plugin).isNotNull()
    }

    @Test
    fun `disconnect should not throw`() {
        plugin.disconnect("test")

        assertThat(plugin).isNotNull()
    }

    @Test
    fun `stopConnecting should not throw`() {
        plugin.stopConnecting()

        assertThat(plugin).isNotNull()
    }

    @Test
    fun `getPumpStatus should skip the read when no patch address is stored`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        runBlocking { plugin.getPumpStatus("test") }

        verifyBlocking(bleSession, never()) { readInfusionInfo(any()) }
    }

    @Test
    fun `getPumpStatus should swallow a session failure without throwing`() {
        whenever { bleSession.readInfusionInfo(any()) }.thenAnswer { throw IllegalStateException("unreachable") }

        runBlocking { plugin.getPumpStatus("test") }

        verify(carelevoPatch, never()).applyInfusionInfoReport(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `getPumpStatus should read infusion info over the session and persist it`() {
        runBlocking { plugin.getPumpStatus("test") }

        verifyBlocking(bleSession) { readInfusionInfo(any()) }
        verify(carelevoPatch).applyInfusionInfoReport(
            runningMinutes = 100,
            remains = 60.0,
            infusedTotalBasalAmount = 1.0,
            infusedTotalBolusAmount = 2.0,
            pumpStateRaw = 0,
            modeRaw = 1
        )
    }

    @Test
    fun `timezoneOrDSTChanged should route a CmdTimeZoneUpdate through the command queue`() {
        // Now managed by the queue (connect-before-execute) instead of a direct BLE write, so a resting
        // pump reconnects first. The executor runs the timezone use case on the queue worker thread.
        runBlocking {
            whenever(commandQueue.customCommand(any())).thenReturn(fakePumpEnactResult())

            plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged)

            verify(commandQueue).customCommand(any<CmdTimeZoneUpdate>())
        }
    }
}
