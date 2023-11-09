package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.main.extensions.fromConstant
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

@Suppress("SpellCheckingInspection")
internal class ProfileSwitchExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var insulin: Insulin

    private var insulinConfiguration: InsulinConfiguration = InsulinConfiguration("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun mock() {
        Mockito.`when`(insulin.insulinConfiguration).thenReturn(insulinConfiguration)
        Mockito.`when`(activePlugin.activeInsulin).thenReturn(insulin)
    }

    @Test
    fun toProfileSwitch() {
        var profileSwitch = ProfileSwitch(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(validProfile.units),
            profileName = "SomeProfile",
            timeshift = 0,
            percentage = 100,
            duration = 0,
            insulinConfiguration = activePlugin.activeInsulin.insulinConfiguration.also {
                it.insulinEndTime = (validProfile.dia * 3600 * 1000).toLong()
            },
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil, decimalFormatter).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(activePlugin, dateUtil)!!
        assertThat(profileSwitch.contentEqualsTo(profileSwitch2)).isTrue()
        assertThat(profileSwitch.interfaceIdsEqualsTo(profileSwitch2)).isTrue()

        profileSwitch = ProfileSwitch(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(validProfile.units),
            profileName = "SomeProfile",
            timeshift = -3600000,
            percentage = 150,
            duration = 3600000,
            insulinConfiguration = activePlugin.activeInsulin.insulinConfiguration.also {
                it.insulinEndTime = (validProfile.dia * 3600 * 1000).toLong()
            },
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil, decimalFormatter).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(activePlugin, dateUtil)!!
        assertThat(profileSwitch.contentEqualsTo(profileSwitch2)).isTrue()
        assertThat(profileSwitch.interfaceIdsEqualsTo(profileSwitch2)).isTrue()
    }
}
