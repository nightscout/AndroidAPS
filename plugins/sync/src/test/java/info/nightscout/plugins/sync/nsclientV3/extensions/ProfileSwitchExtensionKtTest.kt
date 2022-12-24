package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.core.extensions.fromConstant
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class ProfileSwitchExtensionKtTest : TestBaseWithProfile() {

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

        var profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(activePlugin, dateUtil)!!
        Assertions.assertTrue(profileSwitch.contentEqualsTo(profileSwitch2))
        Assertions.assertTrue(profileSwitch.interfaceIdsEqualsTo(profileSwitch2))

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

        profileSwitch2 = (profileSwitch.toNSProfileSwitch(dateUtil).convertToRemoteAndBack() as NSProfileSwitch).toProfileSwitch(activePlugin, dateUtil)!!
        Assertions.assertTrue(profileSwitch.contentEqualsTo(profileSwitch2))
        Assertions.assertTrue(profileSwitch.interfaceIdsEqualsTo(profileSwitch2))
    }
}