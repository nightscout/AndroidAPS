package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [BS] toJson: SMB vs meal event type, isSMB flag, and optional pump/nightscout ids. */
class BolusExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
    }

    private fun bolus(type: BS.Type, ids: IDs = IDs()) =
        BS(timestamp = 1000L, amount = 2.5, type = type, iCfg = ICfg("Rapid", 75, 6.0, 1.0), ids = ids)

    @Test
    fun smb_mapsToCorrectionBolus() {
        val json = bolus(BS.Type.SMB).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.CORRECTION_BOLUS.text)
        assertThat(json.getBoolean("isSMB")).isTrue()
        assertThat(json.getDouble("insulin")).isEqualTo(2.5)
        assertThat(json.getString("type")).isEqualTo("SMB")
    }

    @Test
    fun normal_mapsToMealBolus() {
        val json = bolus(BS.Type.NORMAL).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.MEAL_BOLUS.text)
        assertThat(json.getBoolean("isSMB")).isFalse()
    }

    @Test
    fun includesPumpIdsAndNightscoutIdOnAdd() {
        val ids = IDs(pumpId = 7L, pumpType = PumpType.USER, pumpSerial = "S", nightscoutId = "N")
        val json = bolus(BS.Type.NORMAL, ids).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getLong("pumpId")).isEqualTo(7L)
        assertThat(json.getString("pumpType")).isEqualTo("USER")
        assertThat(json.getString("pumpSerial")).isEqualTo("S")
        assertThat(json.getString("_id")).isEqualTo("N")
    }

    @Test
    fun omitsNightscoutIdWhenNotAdd() {
        val json = bolus(BS.Type.NORMAL, IDs(nightscoutId = "N")).toJson(isAdd = false, dateUtil = dateUtil)
        assertThat(json.has("_id")).isFalse()
    }
}
