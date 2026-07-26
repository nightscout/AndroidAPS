package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.CA
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

/** Covers the xdrip [CA] toJson: small-vs-large carbs event type, optional duration and pump/nightscout ids. */
class CarbsExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
    }

    private fun carbs(amount: Double, duration: Long = 0L, ids: IDs = IDs()) =
        CA(timestamp = 1000L, duration = duration, amount = amount, ids = ids)

    @Test
    fun smallCarbs_mapToCarbsCorrection_noDuration() {
        val json = carbs(5.0).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.CARBS_CORRECTION.text)
        assertThat(json.getDouble("carbs")).isEqualTo(5.0)
        assertThat(json.has("duration")).isFalse() // zero duration omitted
    }

    @Test
    fun largeCarbs_mapToMealBolus() {
        val json = carbs(20.0).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.MEAL_BOLUS.text)
    }

    @Test
    fun includesDurationAndIdsWhenPresent() {
        val ids = IDs(pumpId = 3L, pumpType = PumpType.USER, pumpSerial = "S", nightscoutId = "N")
        val json = carbs(20.0, duration = 3_600_000L, ids = ids).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getLong("duration")).isEqualTo(3_600_000L)
        assertThat(json.getLong("pumpId")).isEqualTo(3L)
        assertThat(json.getString("_id")).isEqualTo("N")
    }

    @Test
    fun omitsNightscoutIdWhenNotAdd() {
        val json = carbs(5.0, ids = IDs(nightscoutId = "N")).toJson(isAdd = false, dateUtil = dateUtil)
        assertThat(json.has("_id")).isFalse()
    }
}
