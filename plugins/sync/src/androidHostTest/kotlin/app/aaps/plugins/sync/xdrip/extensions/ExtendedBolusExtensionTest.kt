package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [EB] JSON extensions: toRealJson field mapping (+ optional pump ids) and toJson dispatch. */
class ExtendedBolusExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
    }

    private fun eb(emulating: Boolean = false, ids: IDs = IDs()) = EB(
        timestamp = 1672531200000L,
        duration = T.hours(2).msecs(),
        amount = 5.0,
        isEmulatingTempBasal = emulating,
        ids = ids
    )

    @Test
    fun toRealJson_mapsCoreFields() {
        val bolus = eb()
        val json = bolus.toRealJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.COMBO_BOLUS.text)
        assertThat(json.getString("created_at")).isEqualTo("2023-01-01T00:00:00Z")
        assertThat(json.getDouble("enteredinsulin")).isEqualTo(5.0)
        assertThat(json.getLong("durationInMilliseconds")).isEqualTo(T.hours(2).msecs())
        assertThat(json.getLong("duration")).isEqualTo(120L) // minutes
        assertThat(json.getInt("splitNow")).isEqualTo(0)
        assertThat(json.getInt("splitExt")).isEqualTo(100)
        assertThat(json.getDouble("relative")).isEqualTo(bolus.rate)
        assertThat(json.getBoolean("isEmulatingTempBasal")).isFalse()
        assertThat(json.has("pumpId")).isFalse()
    }

    @Test
    fun toRealJson_includesPumpIdsWhenPresent() {
        val ids = IDs(pumpId = 11L, endId = 22L, pumpType = PumpType.USER, pumpSerial = "SER", nightscoutId = "NS")
        val json = eb(ids = ids).toRealJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getLong("pumpId")).isEqualTo(11L)
        assertThat(json.getLong("endId")).isEqualTo(22L)
        assertThat(json.getString("pumpType")).isEqualTo("USER")
        assertThat(json.getString("pumpSerial")).isEqualTo("SER")
        assertThat(json.getString("_id")).isEqualTo("NS")
    }

    @Test
    fun toRealJson_omitsNightscoutIdWhenNotAdd() {
        val json = eb(ids = IDs(nightscoutId = "NS")).toRealJson(isAdd = false, dateUtil = dateUtil)
        assertThat(json.has("_id")).isFalse()
    }

    @Test
    fun toJson_nullProfileReturnsNull() {
        assertThat(eb().toJson(isAdd = true, profile = null, dateUtil = dateUtil)).isNull()
    }

    @Test
    fun toJson_nonEmulatingReturnsRealJson() {
        val json = eb(emulating = false).toJson(isAdd = true, profile = mock<Profile>(), dateUtil = dateUtil)
        assertThat(json).isNotNull()
        assertThat(json!!.getString("eventType")).isEqualTo(TE.Type.COMBO_BOLUS.text)
    }
}
