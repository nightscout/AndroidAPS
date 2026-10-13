package app.aaps.plugins.aps.autotune.data

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class BGDatumTest : TestBaseWithProfile() {

    @Test
    fun `default constructor creates empty BGDatum`() {
        val bgDatum = BGDatum(dateUtil)

        assertThat(bgDatum.id).isEqualTo(0)
        assertThat(bgDatum.date).isEqualTo(0L)
        assertThat(bgDatum.value).isEqualTo(0.0)
        assertThat(bgDatum.deviation).isEqualTo(0.0)
        assertThat(bgDatum.bgi).isEqualTo(0.0)
        assertThat(bgDatum.avgDelta).isEqualTo(0.0)
        assertThat(bgDatum.mealCarbs).isEqualTo(0)
        assertThat(bgDatum.mealAbsorption).isEqualTo("")
        assertThat(bgDatum.uamAbsorption).isEqualTo("")
    }

    @Test
    fun `constructor from JSON parses all fields`() {
        val json = JSONObject().apply {
            put("date", 1609459200000L)
            put("sgv", 120.0)
            put("direction", "Flat")
            put("deviation", 5.0)
            put("BGI", -2.0)
            put("avgDelta", 3.0)
            put("mealAbsorption", "full")
            put("mealCarbs", 30)
        }

        val bgDatum = BGDatum(json, dateUtil)

        assertThat(bgDatum.date).isEqualTo(1609459200000L)
        assertThat(bgDatum.value).isEqualTo(120.0)
        assertThat(bgDatum.direction).isEqualTo(TrendArrow.FLAT)
        assertThat(bgDatum.deviation).isEqualTo(5.0)
        assertThat(bgDatum.bgi).isEqualTo(-2.0)
        assertThat(bgDatum.avgDelta).isEqualTo(3.0)
        assertThat(bgDatum.mealAbsorption).isEqualTo("full")
        assertThat(bgDatum.mealCarbs).isEqualTo(30)
    }

    @Test
    fun `constructor from JSON handles missing fields`() {
        val json = JSONObject().apply {
            put("date", 1609459200000L)
            put("sgv", 120.0)
        }

        val bgDatum = BGDatum(json, dateUtil)

        assertThat(bgDatum.date).isEqualTo(1609459200000L)
        assertThat(bgDatum.value).isEqualTo(120.0)
        assertThat(bgDatum.deviation).isEqualTo(0.0)
        assertThat(bgDatum.bgi).isEqualTo(0.0)
        assertThat(bgDatum.mealCarbs).isEqualTo(0)
    }

    @Test
    fun `constructor from JSON handles empty JSON`() {
        val json = JSONObject()

        val bgDatum = BGDatum(json, dateUtil)

        assertThat(bgDatum.date).isEqualTo(0L)
        assertThat(bgDatum.value).isEqualTo(0.0)
    }

    @Test
    fun `constructor from GlucoseValue copies values`() {
        val gv = GV(
            timestamp = 1609459200000L,
            value = 150.0,
            raw = 0.0,
            noise = 0.0,
            trendArrow = TrendArrow.SINGLE_UP,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE
        ).apply {
            id = 42L
        }

        val bgDatum = BGDatum(gv, dateUtil)

        assertThat(bgDatum.id).isEqualTo(42L)
        assertThat(bgDatum.date).isEqualTo(1609459200000L)
        assertThat(bgDatum.value).isEqualTo(150.0)
        assertThat(bgDatum.direction).isEqualTo(TrendArrow.SINGLE_UP)
        assertThat(bgDatum.bgReading).isEqualTo(gv)
    }

    @Test
    fun `toJSON without meal data includes basic fields`() {
        val bgDatum = BGDatum(dateUtil).apply {
            id = 123L
            date = 1609459200000L
            value = 110.0
            direction = TrendArrow.FLAT
            avgDelta = 2.0
            bgi = -1.5
            deviation = 3.5
            mealAbsorption = "partial"
            mealCarbs = 20
        }

        whenever(dateUtil.now()).thenReturn(1609459200000L)
        whenever(dateUtil.toISOAsUTC(1609459200000L)).thenReturn("2021-01-01T00:00:00.000Z")

        val json = bgDatum.toJSON(mealData = false)

        assertThat(json.getLong("_id")).isEqualTo(123L)
        assertThat(json.getLong("date")).isEqualTo(1609459200000L)
        assertThat(json.getDouble("sgv")).isEqualTo(110.0)
        assertThat(json.getDouble("avgDelta")).isEqualTo(2.0)
        assertThat(json.getDouble("BGI")).isEqualTo(-1.5)
        assertThat(json.getDouble("deviation")).isEqualTo(3.5)
        assertThat(json.getString("type")).isEqualTo("sgv")

        // Meal data should NOT be included
        assertThat(json.has("mealAbsorption")).isFalse()
        assertThat(json.has("mealCarbs")).isFalse()
    }

    @Test
    fun `toJSON with meal data includes meal fields`() {
        val bgDatum = BGDatum(dateUtil).apply {
            id = 123L
            date = 1609459200000L
            value = 110.0
            mealAbsorption = "partial"
            mealCarbs = 20
        }

        whenever(dateUtil.now()).thenReturn(1609459200000L)
        whenever(dateUtil.toISOAsUTC(1609459200000L)).thenReturn("2021-01-01T00:00:00.000Z")

        val json = bgDatum.toJSON(mealData = true)

        assertThat(json.getString("mealAbsorption")).isEqualTo("partial")
        assertThat(json.getInt("mealCarbs")).isEqualTo(20)
    }

    @Test
    fun `equals returns true for same values`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            deviation = 5.0
            avgDelta = 3.0
            bgi = -2.0
            mealAbsorption = "full"
            mealCarbs = 30
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            deviation = 5.0
            avgDelta = 3.0
            bgi = -2.0
            mealAbsorption = "full"
            mealCarbs = 30
        }

        assertThat(bgDatum1.equals(bgDatum2)).isTrue()
    }

    @Test
    fun `equals returns false for different dates`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459300000L  // Different date
        }

        assertThat(bgDatum1.equals(bgDatum2)).isFalse()
    }

    @Test
    fun `equals compares dates with second precision`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L  // Exactly at second
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200500L  // Same second, different milliseconds
        }

        // Should be equal because dates are compared at second precision
        assertThat(bgDatum1.equals(bgDatum2)).isTrue()
    }

    @Test
    fun `equals returns false for different deviation`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            deviation = 5.0
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            deviation = 6.0  // Different deviation
        }

        assertThat(bgDatum1.equals(bgDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different avgDelta`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            avgDelta = 3.0
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            avgDelta = 4.0  // Different avgDelta
        }

        assertThat(bgDatum1.equals(bgDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different bgi`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            bgi = -2.0
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            bgi = -3.0  // Different bgi
        }

        assertThat(bgDatum1.equals(bgDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different mealAbsorption`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            mealAbsorption = "full"
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            mealAbsorption = "partial"  // Different mealAbsorption
        }

        assertThat(bgDatum1.equals(bgDatum2)).isFalse()
    }

    @Test
    fun `equals returns false for different mealCarbs`() {
        val bgDatum1 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            mealCarbs = 30
        }

        val bgDatum2 = BGDatum(dateUtil).apply {
            date = 1609459200000L
            mealCarbs = 40  // Different mealCarbs
        }

        assertThat(bgDatum1.equals(bgDatum2)).isFalse()
    }

    @Test
    fun `toJSON includes dateString and sysTime`() {
        val bgDatum = BGDatum(dateUtil).apply {
            id = 123L
            date = 1609459200000L
            value = 110.0
        }

        whenever(dateUtil.now()).thenReturn(1609459200000L)
        whenever(dateUtil.toISOAsUTC(1609459200000L)).thenReturn("2021-01-01T00:00:00.000Z")

        val json = bgDatum.toJSON(mealData = false)

        assertThat(json.getString("dateString")).isEqualTo("2021-01-01T00:00:00.000Z")
        assertThat(json.getString("sysTime")).isEqualTo("2021-01-01T00:00:00.000Z")
    }

    @Test
    fun `toJSON includes glucose field with same value as sgv`() {
        val bgDatum = BGDatum(dateUtil).apply {
            id = 123L
            date = 1609459200000L
            value = 125.0
        }

        whenever(dateUtil.now()).thenReturn(1609459200000L)
        whenever(dateUtil.toISOAsUTC(1609459200000L)).thenReturn("2021-01-01T00:00:00.000Z")

        val json = bgDatum.toJSON(mealData = false)

        assertThat(json.getDouble("glucose")).isEqualTo(125.0)
        assertThat(json.getDouble("sgv")).isEqualTo(125.0)
    }

    @Test
    fun `constructor from JSON parses all TrendArrow directions`() {
        val directions = listOf("DoubleUp", "SingleUp", "FortyFiveUp", "Flat", "FortyFiveDown", "SingleDown", "DoubleDown")
        val expected = listOf(
            TrendArrow.DOUBLE_UP, TrendArrow.SINGLE_UP, TrendArrow.FORTY_FIVE_UP,
            TrendArrow.FLAT, TrendArrow.FORTY_FIVE_DOWN, TrendArrow.SINGLE_DOWN, TrendArrow.DOUBLE_DOWN
        )

        directions.forEachIndexed { index, direction ->
            val json = JSONObject().apply {
                put("direction", direction)
            }

            val bgDatum = BGDatum(json, dateUtil)

            assertThat(bgDatum.direction).isEqualTo(expected[index])
        }
    }
}
