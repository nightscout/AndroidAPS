package app.aaps.plugins.aps.autotune.data

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class CRDatumTest : TestBaseWithProfile() {

    @Test
    fun `default constructor creates empty CRDatum`() {
        val crDatum = CRDatum(dateUtil)

        assertThat(crDatum.crInitialIOB).isEqualTo(0.0)
        assertThat(crDatum.crInitialBG).isEqualTo(0.0)
        assertThat(crDatum.crInitialCarbTime).isEqualTo(0L)
        assertThat(crDatum.crEndIOB).isEqualTo(0.0)
        assertThat(crDatum.crEndBG).isEqualTo(0.0)
        assertThat(crDatum.crEndTime).isEqualTo(0L)
        assertThat(crDatum.crCarbs).isEqualTo(0.0)
        assertThat(crDatum.crInsulin).isEqualTo(0.0)
        assertThat(crDatum.crInsulinTotal).isEqualTo(0.0)
    }

    @Test
    fun `constructor from JSON parses all fields`() {
        whenever(dateUtil.fromISODateString("2021-01-01T00:00:00.000Z")).thenReturn(1609459200000L)
        whenever(dateUtil.fromISODateString("2021-01-01T04:00:00.000Z")).thenReturn(1609473600000L)

        val json = JSONObject().apply {
            put("CRInitialIOB", 2.5)
            put("CRInitialBG", 120.0)
            put("CRInitialCarbTime", "2021-01-01T00:00:00.000Z")
            put("CREndIOB", 1.0)
            put("CREndBG", 100.0)
            put("CREndTime", "2021-01-01T04:00:00.000Z")
            put("CRCarbs", 30.0)
            put("CRInsulin", 3.0)
        }

        val crDatum = CRDatum(json, dateUtil)

        assertThat(crDatum.crInitialIOB).isEqualTo(2.5)
        assertThat(crDatum.crInitialBG).isEqualTo(120.0)
        assertThat(crDatum.crInitialCarbTime).isEqualTo(1609459200000L)
        assertThat(crDatum.crEndIOB).isEqualTo(1.0)
        assertThat(crDatum.crEndBG).isEqualTo(100.0)
        assertThat(crDatum.crEndTime).isEqualTo(1609473600000L)
        assertThat(crDatum.crCarbs).isEqualTo(30.0)
        assertThat(crDatum.crInsulin).isEqualTo(3.0)
    }

    @Test
    fun `constructor from JSON handles missing fields`() {
        val json = JSONObject().apply {
            put("CRInitialBG", 120.0)
            put("CRCarbs", 30.0)
        }

        val crDatum = CRDatum(json, dateUtil)

        assertThat(crDatum.crInitialBG).isEqualTo(120.0)
        assertThat(crDatum.crCarbs).isEqualTo(30.0)
        assertThat(crDatum.crInitialIOB).isEqualTo(0.0)
        assertThat(crDatum.crEndIOB).isEqualTo(0.0)
    }

    @Test
    fun `constructor from JSON handles empty JSON`() {
        val json = JSONObject()

        val crDatum = CRDatum(json, dateUtil)

        assertThat(crDatum.crInitialIOB).isEqualTo(0.0)
        assertThat(crDatum.crInitialBG).isEqualTo(0.0)
        assertThat(crDatum.crInitialCarbTime).isEqualTo(0L)
    }

    @Test
    fun `toJSON includes all fields`() {
        val crDatum = CRDatum(dateUtil).apply {
            crInitialIOB = 2.5
            crInitialBG = 120.0
            crInitialCarbTime = 1609459200000L
            crEndIOB = 1.0
            crEndBG = 100.0
            crEndTime = 1609473600000L
            crCarbs = 30.0
            crInsulin = 3.0
        }

        whenever(dateUtil.toISOString(1609459200000L)).thenReturn("2021-01-01T00:00:00.000Z")
        whenever(dateUtil.toISOString(1609473600000L)).thenReturn("2021-01-01T04:00:00.000Z")

        val json = crDatum.toJSON()

        assertThat(json.getDouble("CRInitialIOB")).isEqualTo(2.5)
        assertThat(json.getInt("CRInitialBG")).isEqualTo(120)
        assertThat(json.getString("CRInitialCarbTime")).isEqualTo("2021-01-01T00:00:00.000Z")
        assertThat(json.getDouble("CREndIOB")).isEqualTo(1.0)
        assertThat(json.getInt("CREndBG")).isEqualTo(100)
        assertThat(json.getString("CREndTime")).isEqualTo("2021-01-01T04:00:00.000Z")
        assertThat(json.getInt("CRCarbs")).isEqualTo(30)
        assertThat(json.getDouble("CRInsulin")).isEqualTo(3.0)
    }

    @Test
    fun `toJSON converts BG values to integers`() {
        val crDatum = CRDatum(dateUtil).apply {
            crInitialBG = 120.7
            crEndBG = 100.3
        }

        whenever(dateUtil.toISOString(0L)).thenReturn("1970-01-01T00:00:00.000Z")

        val json = crDatum.toJSON()

        assertThat(json.getInt("CRInitialBG")).isEqualTo(120)
        assertThat(json.getInt("CREndBG")).isEqualTo(100)
    }

    @Test
    fun `toJSON converts carbs to integers`() {
        val crDatum = CRDatum(dateUtil).apply {
            crCarbs = 30.8
        }

        whenever(dateUtil.toISOString(0L)).thenReturn("1970-01-01T00:00:00.000Z")

        val json = crDatum.toJSON()

        assertThat(json.getInt("CRCarbs")).isEqualTo(30)
    }

    @Test
    fun `equals returns true for same values`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crInitialIOB = 2.5
            crInitialBG = 120.0
            crInitialCarbTime = 1609459200000L
            crEndIOB = 1.0
            crEndBG = 100.0
            crEndTime = 1609473600000L
            crCarbs = 30.0
            crInsulin = 3.0
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crInitialIOB = 2.5
            crInitialBG = 120.0
            crInitialCarbTime = 1609459200000L
            crEndIOB = 1.0
            crEndBG = 100.0
            crEndTime = 1609473600000L
            crCarbs = 30.0
            crInsulin = 3.0
        }

        assertThat(crDatum1.equals(crDatum2)).isTrue()
    }

    @Test
    fun `equals compares timestamps with second precision`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crInitialCarbTime = 1609459200000L  // Exactly at second
            crEndTime = 1609473600000L
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crInitialCarbTime = 1609459200500L  // Same second, different milliseconds
            crEndTime = 1609473600700L
        }

        // Should be equal because timestamps are compared at second precision
        assertThat(crDatum1.equals(crDatum2)).isTrue()
    }

    @Test
    fun `equals returns false for different crInitialIOB`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crInitialIOB = 2.5
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crInitialIOB = 3.0
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crInitialBG`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crInitialBG = 120.0
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crInitialBG = 130.0
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crInitialCarbTime`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crInitialCarbTime = 1609459200000L
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crInitialCarbTime = 1609459300000L  // Different second
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crEndIOB`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crEndIOB = 1.0
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crEndIOB = 1.5
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crEndBG`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crEndBG = 100.0
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crEndBG = 110.0
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crEndTime`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crEndTime = 1609473600000L
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crEndTime = 1609473700000L  // Different second
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crCarbs`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crCarbs = 30.0
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crCarbs = 40.0
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different crInsulin`() {
        val crDatum1 = CRDatum(dateUtil).apply {
            crInsulin = 3.0
        }

        val crDatum2 = CRDatum(dateUtil).apply {
            crInsulin = 4.0
        }

        assertThat(crDatum1.equals(crDatum2)).isFalse()
    }

    @Test
    fun `handles zero values`() {
        val crDatum = CRDatum(dateUtil).apply {
            crInitialIOB = 0.0
            crInitialBG = 0.0
            crEndIOB = 0.0
            crEndBG = 0.0
            crCarbs = 0.0
            crInsulin = 0.0
        }

        whenever(dateUtil.toISOString(0L)).thenReturn("1970-01-01T00:00:00.000Z")

        val json = crDatum.toJSON()

        assertThat(json.getDouble("CRInitialIOB")).isEqualTo(0.0)
        assertThat(json.getInt("CRInitialBG")).isEqualTo(0)
        assertThat(json.getInt("CRCarbs")).isEqualTo(0)
    }

    @Test
    fun `handles large values`() {
        val crDatum = CRDatum(dateUtil).apply {
            crInitialIOB = 100.0
            crInitialBG = 400.0
            crCarbs = 500.0
            crInsulin = 50.0
        }

        whenever(dateUtil.toISOString(0L)).thenReturn("1970-01-01T00:00:00.000Z")

        val json = crDatum.toJSON()

        assertThat(json.getDouble("CRInitialIOB")).isEqualTo(100.0)
        assertThat(json.getInt("CRInitialBG")).isEqualTo(400)
        assertThat(json.getInt("CRCarbs")).isEqualTo(500)
        assertThat(json.getDouble("CRInsulin")).isEqualTo(50.0)
    }

    @Test
    fun `handles fractional IOB and insulin values`() {
        val crDatum = CRDatum(dateUtil).apply {
            crInitialIOB = 2.567
            crEndIOB = 1.234
            crInsulin = 3.789
        }

        whenever(dateUtil.toISOString(0L)).thenReturn("1970-01-01T00:00:00.000Z")

        val json = crDatum.toJSON()

        assertThat(json.getDouble("CRInitialIOB")).isEqualTo(2.567)
        assertThat(json.getDouble("CREndIOB")).isEqualTo(1.234)
        assertThat(json.getDouble("CRInsulin")).isEqualTo(3.789)
    }
}
