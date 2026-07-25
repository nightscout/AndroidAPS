package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

/** Covers the xdrip [PS] toJson: field mapping (values captured before the customization reset), embedded profileJson, pump ids. */
internal class ProfileSwitchExtensionTest : TestBaseWithProfile() {

    private val insulinConfiguration: ICfg = ICfg("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun mock() {
        whenever(insulin.iCfg).thenReturn(insulinConfiguration)
    }

    private fun ps(ids: IDs = IDs()) = PS(
        timestamp = 10000, isValid = true,
        basalBlocks = effectiveProfile.basalBlocks, isfBlocks = effectiveProfile.isfBlocks,
        icBlocks = effectiveProfile.icBlocks, targetBlocks = effectiveProfile.targetBlocks,
        glucoseUnit = effectiveProfile.units, profileName = "SomeProfile",
        timeshift = 3600000, percentage = 150, duration = 3600000,
        iCfg = insulin.iCfg.also { it.insulinEndTime = (effectiveProfile.iCfg.dia * 3600 * 1000).toLong() },
        ids = ids
    )

    @Test
    fun toJson_mapsFieldsBeforeCustomizationReset() {
        val json = ps().toJson(isAdd = true, dateUtil = dateUtil, decimalFormatter = decimalFormatter)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.PROFILE_SWITCH.text)
        assertThat(json.getInt("percentage")).isEqualTo(150) // captured before the reset to 100
        assertThat(json.getString("originalProfileName")).isEqualTo("SomeProfile")
        assertThat(json.getLong("originalDuration")).isEqualTo(3600000)
        assertThat(json.has("profileJson")).isTrue()
    }

    @Test
    fun includesPumpIds() {
        val json = ps(IDs(pumpId = 11000, pumpType = PumpType.DANA_I, pumpSerial = "bbbb", nightscoutId = "N"))
            .toJson(isAdd = true, dateUtil = dateUtil, decimalFormatter = decimalFormatter)
        assertThat(json.getLong("pumpId")).isEqualTo(11000)
        assertThat(json.getString("pumpType")).isEqualTo("DANA_I")
        assertThat(json.getString("_id")).isEqualTo("N")
    }

    @Test
    fun omitsNightscoutIdWhenNotAdd() {
        val json = ps(IDs(nightscoutId = "N")).toJson(isAdd = false, dateUtil = dateUtil, decimalFormatter = decimalFormatter)
        assertThat(json.has("_id")).isFalse()
    }
}
