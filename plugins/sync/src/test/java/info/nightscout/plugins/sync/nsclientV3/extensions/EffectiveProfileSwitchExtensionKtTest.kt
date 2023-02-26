package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class EffectiveProfileSwitchExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toEffectiveProfileSwitch() {
        val profileSwitch = EffectiveProfileSwitch(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = EffectiveProfileSwitch.GlucoseUnit.fromConstant(validProfile.units),
            originalProfileName = "SomeProfile",
            originalCustomizedName = "SomeProfile (150%, 1h)",
            originalTimeshift = 3600000,
            originalPercentage = 150,
            originalDuration = 3600000,
            originalEnd = 0,
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

        val profileSwitch2 = (profileSwitch.toNSEffectiveProfileSwitch(dateUtil).convertToRemoteAndBack() as NSEffectiveProfileSwitch).toEffectiveProfileSwitch(dateUtil)!!
        Assertions.assertTrue(profileSwitch.contentEqualsTo(profileSwitch2))
        Assertions.assertTrue(profileSwitch.interfaceIdsEqualsTo(profileSwitch2))
    }
}