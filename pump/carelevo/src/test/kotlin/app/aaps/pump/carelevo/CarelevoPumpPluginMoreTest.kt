package app.aaps.pump.carelevo

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.carelevo.command.CmdPumpResume
import app.aaps.pump.carelevo.command.CmdTimeZoneUpdate
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.PatchState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Gap-closing companion to [CarelevoPumpPluginTest] / [CarelevoPumpPluginBolusTest] /
 * [CarelevoPumpPluginTempBasalTest] / [CarelevoPumpPluginStatusTest].
 *
 * Covers the plugin surface those suites leave untouched: the `isConfigured` activation gate, the
 * `setNewBasalProfile` state branch, the CustomCommand routing to
 * [app.aaps.pump.carelevo.command.CarelevoActivationExecutor], the `_lastDataTime` bookkeeping, the
 * remaining `timezoneOrDSTChanged` branches and the preference-screen definition.
 *
 * The lifecycle half of the plugin (onStart/onStop, the preference observers, the deferred
 * settings-sync recovery and handleAlarms) needs a real Looper/ProcessLifecycleOwner and lives in
 * the Robolectric suite [CarelevoPumpPluginLifecycleTest].
 */
class CarelevoPumpPluginMoreTest : CarelevoPumpPluginTestBase() {

    private fun pumpProfile(): PumpProfile = mock<PumpProfile>().also {
        whenever(it.getBasal()).thenReturn(1.0)
    }

    // ---- isConfigured (activation gate) --------------------------------------------------------

    @Test
    fun `isConfigured should be true once activation persisted a patch record`() {
        assertThat(plugin.isConfigured()).isTrue()
    }

    @Test
    fun `isConfigured should be false when no patch record exists`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isConfigured()).isFalse()
    }

    @Test
    fun `not configured should imply not initialized`() {
        // Contract invariant: isInitialized() gates on the same patchInfo signal first, so
        // !isConfigured() => !isInitialized() must hold by construction.
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isConfigured()).isFalse()
        assertThat(plugin.isInitialized()).isFalse()
    }

    // ---- setNewBasalProfile ---------------------------------------------------------------------

    @Test
    fun `setNewBasalProfile should store the profile without enacting when no patch is active`() {
        // NotConnectedNotBooting = no patch yet: the profile is only cached for a later activation, so
        // this is a deferred write (enacted=false, no PROFILE_SET_OK) that must still report success=true
        // to keep the not-ready case out of the central failure alarm.
        doReturn(PatchState.NotConnectedNotBooting).whenever(carelevoPatch).resolvePatchState()
        val profile = pumpProfile()

        val result = runBlocking { plugin.setNewBasalProfile(profile) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(carelevoPatch).setProfile(profile)
    }

    @Test
    fun `setNewBasalProfile should not touch the BLE session when no patch is active`() {
        doReturn(PatchState.NotConnectedNotBooting).whenever(carelevoPatch).resolvePatchState()

        runBlocking { plugin.setNewBasalProfile(pumpProfile()) }

        verifyBlocking(bleSession, never()) { runBasalProgram(any(), any(), any()) }
    }

    @Test
    fun `setNewBasalProfile should push the program to the patch when a patch is present`() {
        val profile = pumpProfile()

        val result = runBlocking { plugin.setNewBasalProfile(profile) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(bleSession) { runBasalProgram(any(), any(), any()) }
        verify(carelevoPatch).setProfile(profile)
    }

    @Test
    fun `setNewBasalProfile should fail when the basal program write is rejected`() {
        whenever { bleSession.runBasalProgram(any(), any(), any()) }.thenReturn(false)

        val result = runBlocking { plugin.setNewBasalProfile(pumpProfile()) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setNewBasalProfile should refresh lastDataTime`() {
        assertThat(plugin.lastDataTime.value).isEqualTo(0L)

        runBlocking { plugin.setNewBasalProfile(pumpProfile()) }

        assertThat(plugin.lastDataTime.value).isGreaterThan(0L)
    }

    // ---- isThisProfileSet -----------------------------------------------------------------------

    @Test
    fun `isThisProfileSet should delegate to the patch profile comparison`() {
        val profile = pumpProfile()
        whenever(carelevoPatch.checkIsSameProfile(profile)).thenReturn(true)

        assertThat(plugin.isThisProfileSet(profile)).isTrue()
        verify(carelevoPatch).checkIsSameProfile(profile)
    }

    @Test
    fun `isThisProfileSet should return false when the patch reports a different profile`() {
        val profile = pumpProfile()
        whenever(carelevoPatch.checkIsSameProfile(profile)).thenReturn(false)

        assertThat(plugin.isThisProfileSet(profile)).isFalse()
    }

    // ---- executeCustomCommand (queue routing) ---------------------------------------------------

    @Test
    fun `executeCustomCommand should return the activation executor result`() {
        val command = CmdPumpResume()
        val expected = fakePumpEnactResult()
        whenever(activationExecutor.execute(command)).thenReturn(expected)

        assertThat(plugin.executeCustomCommand(command)).isSameInstanceAs(expected)
    }

    @Test
    fun `executeCustomCommand should forward an unknown command and return the executor null`() {
        // Unknown commands are the executor's call, not the plugin's — the plugin is a pure router.
        val unknown = object : CustomCommand {
            override val statusDescription: String = "UNKNOWN"
        }
        whenever(activationExecutor.execute(unknown)).thenReturn(null)

        assertThat(plugin.executeCustomCommand(unknown)).isNull()
        verify(activationExecutor).execute(unknown)
    }

    // ---- basal rate / bolus state delegation ----------------------------------------------------

    @Test
    fun `baseBasalRate should return the current profile basal`() {
        assertThat(plugin.baseBasalRate.cU).isWithin(0.001).of(1.0)
    }

    @Test
    fun `lastBolusTime and lastBolusAmount should delegate to the bolus coordinator defaults`() {
        assertThat(plugin.lastBolusTime.value).isNull()
        assertThat(plugin.lastBolusAmount.value).isNull()
    }

    @Test
    fun `stopBolusDelivering should be a no-op when no patch address is stored`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        plugin.stopBolusDelivering()

        // Delegated to the bolus coordinator, which bails out before opening a session.
        verify(carelevoPatch).getPatchInfoAddress()
    }

    // ---- lastDataTime ---------------------------------------------------------------------------

    @Test
    fun `lastDataTime should start at zero`() {
        assertThat(plugin.lastDataTime.value).isEqualTo(0L)
    }

    @Test
    fun `getPumpStatus should refresh lastDataTime after a successful read`() {
        runBlocking { plugin.getPumpStatus("test") }

        assertThat(plugin.lastDataTime.value).isGreaterThan(0L)
    }

    @Test
    fun `getPumpStatus should leave lastDataTime untouched when the read fails`() {
        whenever { bleSession.readInfusionInfo(any()) }.thenAnswer { throw IllegalStateException("unreachable") }

        runBlocking { plugin.getPumpStatus("test") }

        assertThat(plugin.lastDataTime.value).isEqualTo(0L)
    }

    // ---- timezoneOrDSTChanged -------------------------------------------------------------------

    @Test
    fun `timezoneOrDSTChanged should skip the queue when no patch is active`() {
        patchInfoSubject.onNext(Optional.empty())

        runBlocking { plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }

        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `timezoneOrDSTChanged should carry the remaining insulin to the patch clock update`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo(insulinRemain = 42.7)))
        whenever { commandQueue.customCommand(any()) }.thenReturn(fakePumpEnactResult())

        runBlocking { plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }

        val captor = argumentCaptor<CustomCommand>()
        verifyBlocking(commandQueue) { customCommand(captor.capture()) }
        assertThat((captor.firstValue as CmdTimeZoneUpdate).insulinAmount).isEqualTo(42)
    }

    @Test
    fun `timezoneOrDSTChanged should still push with zero insulin when the remaining amount is unknown`() {
        // Patch present but its reservoir was not read yet (e.g. before the first status read after a
        // reconnect): the clock update must still go out rather than being dropped.
        patchInfoSubject.onNext(Optional.of(samplePatchInfo().copy(insulinRemain = null)))
        whenever { commandQueue.customCommand(any()) }.thenReturn(fakePumpEnactResult())

        runBlocking { plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }

        val captor = argumentCaptor<CustomCommand>()
        verifyBlocking(commandQueue) { customCommand(captor.capture()) }
        assertThat((captor.firstValue as CmdTimeZoneUpdate).insulinAmount).isEqualTo(0)
    }

    @Test
    fun `timezoneOrDSTChanged should refresh lastDataTime only when the queue reports success`() {
        whenever { commandQueue.customCommand(any()) }.thenReturn(fakePumpEnactResult(success = true))

        runBlocking { plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }

        assertThat(plugin.lastDataTime.value).isGreaterThan(0L)
    }

    @Test
    fun `timezoneOrDSTChanged should leave lastDataTime untouched when the queue reports failure`() {
        whenever { commandQueue.customCommand(any()) }.thenReturn(fakePumpEnactResult(success = false))

        runBlocking { plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }

        assertThat(plugin.lastDataTime.value).isEqualTo(0L)
    }

    // ---- static descriptors ---------------------------------------------------------------------

    @Test
    fun `pumpDescription should be filled for the Carelevo pump type`() {
        assertThat(plugin.pumpDescription.pumpType).isEqualTo(PumpType.CAREMEDI_CARELEVO)
    }

    @Test
    fun `pumpDescription should be the same cached instance on every read`() {
        assertThat(plugin.pumpDescription).isSameInstanceAs(plugin.pumpDescription)
    }

    @Test
    fun `serialNumber should be empty when no patch record exists`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.serialNumber()).isEmpty()
    }

    @Test
    fun `getPreferenceScreenContent should expose the three carelevo patch settings`() {
        val screen = plugin.getPreferenceScreenContent()

        assertThat(screen.key).isEqualTo("carelevo_settings")
        assertThat(screen.titleResId).isEqualTo(R.string.carelevo)
        assertThat(screen.items).hasSize(3)
        assertThat((screen.items[0] as IntPreferenceKey).key)
            .isEqualTo(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key)
        assertThat((screen.items[1] as IntPreferenceKey).key)
            .isEqualTo(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key)
        assertThat(screen.items[2]).isEqualTo(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)
    }

    @Test
    fun `getPreferenceScreenContent should attach the plugin icon`() {
        assertThat(plugin.getPreferenceScreenContent().icon).isNotNull()
    }

    @Test
    fun `isSuspended should be false when no patch record exists`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isSuspended()).isFalse()
    }
}
