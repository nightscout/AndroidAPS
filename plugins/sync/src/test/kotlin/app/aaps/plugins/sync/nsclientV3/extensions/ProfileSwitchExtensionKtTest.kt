package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.PS
import app.aaps.core.data.pump.defs.PumpType
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

        var profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil, decimalFormatter).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(localProfileManager, dateUtil, insulin)!!
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

        profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil, decimalFormatter).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(localProfileManager, dateUtil, insulin)!!
        assertThat(profileSwitch.contentEqualsTo(profileSwitch2)).isTrue()
        assertThat(profileSwitch.ids.contentEqualsTo(profileSwitch2.ids)).isTrue()
    }
}
