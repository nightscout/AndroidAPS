package app.aaps.pump.medtrum

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.keys.MedtrumStringNonKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** #2454: AAPS keeps the basal the patch really runs as the running basal, one rate per hour. */
class MedtrumPluginDeliveredBasalTest : MedtrumTestBase() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var plugin: MedtrumPlugin

    @BeforeEach
    fun preparePlugin() {
        plugin = MedtrumPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, dateUtil, medtrumPump, temporaryBasalStorage,
            pumpEnactResultProvider, protectionCheck, blePreCheck, notificationManager
        )
    }

    private fun activePatch() {
        whenever(preferences.get(MedtrumStringNonKey.SnInput)).thenReturn("AB12345")
        medtrumPump.pumpState = MedtrumPumpState.ACTIVE
    }

    private fun profileOf(vararg values: Pair<Int, Double>): Profile {
        val profile = mock<Profile>()
        val basalValues = values.map { (time, rate) -> Profile.ProfileValue(time, rate) }.toTypedArray()
        whenever(profile.getBasalValues()).thenReturn(basalValues)
        return profile
    }

    /** Each rate is rounded to the nearest 0.05 U/h, and an hour with two rates counts each one for its half. */
    @Test
    fun deliveredBasalIsTheProfileThePatchAccepted() {
        activePatch()
        val profile = profileOf(0 to 0.42, 6 * 3600 to 0.43, 10 * 3600 to 0.5, 10 * 3600 + 1800 to 1.0)
        medtrumPump.actualBasalProfile = medtrumPump.buildMedtrumProfileArray(profile)!!

        val delivered = plugin.deliveredBasal()!!

        assertThat(delivered.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
        val expected = (0..23).map {
            when {
                it < 6   -> 0.40
                it < 10  -> 0.45
                it == 10 -> 0.75
                else     -> 1.0
            }
        }
        assertThat(delivered.map { it.value }).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun deliveredBasalIsUnknownWithoutAnActivePatch() {
        activePatch()
        medtrumPump.actualBasalProfile = medtrumPump.buildMedtrumProfileArray(profileOf(0 to 0.5))!!
        medtrumPump.pumpState = MedtrumPumpState.STOPPED

        assertThat(plugin.deliveredBasal()).isNull()
    }

    @Test
    fun deliveredBasalIsUnknownBeforeThePatchAcceptedAProfile() {
        activePatch()
        medtrumPump.actualBasalProfile = byteArrayOf(0)

        assertThat(plugin.deliveredBasal()).isNull()
    }
}
