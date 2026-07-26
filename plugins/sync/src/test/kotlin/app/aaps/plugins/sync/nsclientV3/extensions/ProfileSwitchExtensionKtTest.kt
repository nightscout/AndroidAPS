package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.PS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

internal class ProfileSwitchExtensionKtTest : TestBaseWithProfile() {

    private var insulinConfiguration: ICfg = ICfg("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun mock() {
        whenever(insulin.iCfg).thenReturn(insulinConfiguration)
    }

    @Test
    fun toProfileSwitch() {
        var profileSwitch = PS(
            timestamp = 10000,
            isValid = true,
            basalBlocks = effectiveProfile.basalBlocks,
            isfBlocks = effectiveProfile.isfBlocks,
            icBlocks = effectiveProfile.icBlocks,
            targetBlocks = effectiveProfile.targetBlocks,
            glucoseUnit = effectiveProfile.units,
            profileName = "SomeProfile",
            timeshift = 0,
            percentage = 100,
            duration = 0,
            iCfg = insulin.iCfg.also {
                it.insulinEndTime = (effectiveProfile.iCfg.dia * 3600 * 1000).toLong()
            },
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil, decimalFormatter).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(profileRepository, dateUtil, insulin)!!
        assertThat(profileSwitch.contentEqualsTo(profileSwitch2)).isTrue()
        assertThat(profileSwitch.ids.contentEqualsTo(profileSwitch2.ids)).isTrue()

        profileSwitch = PS(
            timestamp = 10000,
            isValid = true,
            basalBlocks = effectiveProfile.basalBlocks,
            isfBlocks = effectiveProfile.isfBlocks,
            icBlocks = effectiveProfile.icBlocks,
            targetBlocks = effectiveProfile.targetBlocks,
            glucoseUnit = effectiveProfile.units,
            profileName = "SomeProfile",
            timeshift = -3600000,
            percentage = 150,
            duration = 3600000,
            iCfg = insulin.iCfg.also {
                it.insulinEndTime = (effectiveProfile.iCfg.dia * 3600 * 1000).toLong()
            },
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil, decimalFormatter).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(profileRepository, dateUtil, insulin)!!
        assertThat(profileSwitch.contentEqualsTo(profileSwitch2)).isTrue()
        assertThat(profileSwitch.ids.contentEqualsTo(profileSwitch2.ids)).isTrue()
    }

    private fun nsProfileSwitch(duration: Long?, originalDuration: Long?) = NSProfileSwitch(
        date = 10000,
        identifier = "nightscoutId",
        utcOffset = 0,
        isValid = true,
        eventType = EventType.PROFILE_SWITCH,
        pumpId = 11000,
        endId = null,
        pumpType = PumpType.DANA_I.name,
        pumpSerial = "bbbb",
        profileJson = validProfile.toPureNsJson(dateUtil).toString(),
        profile = "SomeProfile",
        originalProfileName = "SomeProfile",
        timeShift = 0,
        percentage = 100,
        duration = duration,
        originalDuration = originalDuration,
        iCfg = null
    )

    /**
     * Documents uploaded before 9b90eea774 carry a duration inflated by 60000 and no
     * durationInMilliseconds, so TreatmentMapper inflates it by 60000 a second time.
     * originalDuration is written in milliseconds by every AAPS version and must win.
     */
    @Test
    fun inflatedLegacyDurationIsIgnoredInFavourOfOriginalDuration() {
        val nineHours = 9 * 3600000L
        val parsed = nsProfileSwitch(duration = nineHours * 60000 * 60000, originalDuration = nineHours)
            .toProfileSwitch(profileRepository, dateUtil, insulin)!!
        assertThat(parsed.duration).isEqualTo(nineHours)
    }

    /** A writer that supplies no originalDuration must still fall back to the mapped duration. */
    @Test
    fun durationIsUsedWhenOriginalDurationIsMissing() {
        val nineHours = 9 * 3600000L
        val parsed = nsProfileSwitch(duration = nineHours, originalDuration = null)
            .toProfileSwitch(profileRepository, dateUtil, insulin)!!
        assertThat(parsed.duration).isEqualTo(nineHours)
    }
}
