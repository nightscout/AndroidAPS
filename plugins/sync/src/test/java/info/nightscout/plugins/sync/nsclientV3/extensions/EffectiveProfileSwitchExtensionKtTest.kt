package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.nssdk.localmodel.treatment.NSEffectiveProfileSwitch
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
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
        assertThat(profileSwitch.contentEqualsTo(profileSwitch2)).isTrue()
        assertThat(profileSwitch.interfaceIdsEqualsTo(profileSwitch2)).isTrue()
    }
}
