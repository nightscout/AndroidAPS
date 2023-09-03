package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.embedments.InsulinConfiguration
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

@Suppress("SpellCheckingInspection")
internal class EffectiveProfileSwitchExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var insulin: Insulin

    private var insulinConfiguration: InsulinConfiguration = InsulinConfiguration("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun mock() {
        Mockito.`when`(insulin.insulinConfiguration).thenReturn(insulinConfiguration)
        Mockito.`when`(activePlugin.activeInsulin).thenReturn(insulin)
    }
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