package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Covers the xdrip [EPS] toJson: the original* field mapping, embedded profileJson, notes and pump ids. */
internal class EffectiveProfileSwitchExtensionTest : TestBaseWithProfile() {

    private val insulinConfiguration: ICfg = ICfg("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun mock() {
    }

    private fun eps(ids: IDs = IDs()) = EPS(
        timestamp = 10000, isValid = true,
        basalBlocks = effectiveProfile.basalBlocks, isfBlocks = effectiveProfile.isfBlocks,
        icBlocks = effectiveProfile.icBlocks, targetBlocks = effectiveProfile.targetBlocks,
        glucoseUnit = effectiveProfile.units,
        originalProfileName = "SomeProfile", originalCustomizedName = "SomeProfile (150%, 1h)",
        originalTimeshift = 3600000, originalPercentage = 150, originalDuration = 3600000, originalEnd = 0,
        iCfg = someICfg.also { it.insulinEndTime = (effectiveProfile.iCfg.dia * 3600 * 1000).toLong() },
        ids = ids
    )

    @Test
    fun toJson_mapsOriginalFields() {
        val json = eps().toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.NOTE.text)
        assertThat(json.getString("originalProfileName")).isEqualTo("SomeProfile")
        assertThat(json.getString("originalCustomizedName")).isEqualTo("SomeProfile (150%, 1h)")
        assertThat(json.getInt("originalPercentage")).isEqualTo(150)
        assertThat(json.getLong("originalTimeshift")).isEqualTo(3600000)
        assertThat(json.getString("notes")).isEqualTo("SomeProfile (150%, 1h)")
        assertThat(json.has("profileJson")).isTrue()
    }

    @Test
    fun includesPumpIdsAndNightscoutId() {
        val json = eps(IDs(pumpId = 11000, pumpType = PumpType.DANA_I, pumpSerial = "bbbb", nightscoutId = "N"))
            .toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getLong("pumpId")).isEqualTo(11000)
        assertThat(json.getString("pumpType")).isEqualTo("DANA_I")
        assertThat(json.getString("_id")).isEqualTo("N")
    }

    @Test
    fun omitsNightscoutIdWhenNotAdd() {
        val json = eps(IDs(nightscoutId = "N")).toJson(isAdd = false, dateUtil = dateUtil)
        assertThat(json.has("_id")).isFalse()
    }
}
