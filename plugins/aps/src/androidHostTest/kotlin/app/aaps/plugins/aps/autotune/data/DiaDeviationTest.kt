package app.aaps.plugins.aps.autotune.data

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class DiaDeviationTest : TestBase() {

    @Test
    fun `default constructor creates empty DiaDeviation`() {
        val diaDeviation = DiaDeviation()

        assertThat(diaDeviation.dia).isEqualTo(0.0)
        assertThat(diaDeviation.meanDeviation).isEqualTo(0.0)
        assertThat(diaDeviation.smrDeviation).isEqualTo(0.0)
        assertThat(diaDeviation.rmsDeviation).isEqualTo(0.0)
    }

    @Test
    fun `constructor with parameters sets all fields`() {
        val diaDeviation = DiaDeviation(
            dia = 6.0,
            meanDeviation = 5.5,
            smrDeviation = 3.2,
            rmsDeviation = 4.8
        )

        assertThat(diaDeviation.dia).isEqualTo(6.0)
        assertThat(diaDeviation.meanDeviation).isEqualTo(5.5)
        assertThat(diaDeviation.smrDeviation).isEqualTo(3.2)
        assertThat(diaDeviation.rmsDeviation).isEqualTo(4.8)
    }

    @Test
    fun `constructor from JSON parses all fields`() {
        val json = JSONObject().apply {
            put("dia", 6.0)
            put("meanDeviation", 5.5)
            put("SMRDeviation", 3.2)
            put("RMSDeviation", 4.8)
        }

        val diaDeviation = DiaDeviation(json)

        assertThat(diaDeviation.dia).isEqualTo(6.0)
        assertThat(diaDeviation.meanDeviation).isEqualTo(5.5)
        assertThat(diaDeviation.smrDeviation).isEqualTo(3.2)
        assertThat(diaDeviation.rmsDeviation).isEqualTo(4.8)
    }

    @Test
    fun `constructor from JSON handles missing fields`() {
        val json = JSONObject().apply {
            put("dia", 6.0)
            put("meanDeviation", 5.5)
        }

        val diaDeviation = DiaDeviation(json)

        assertThat(diaDeviation.dia).isEqualTo(6.0)
        assertThat(diaDeviation.meanDeviation).isEqualTo(5.5)
        assertThat(diaDeviation.smrDeviation).isEqualTo(0.0)
        assertThat(diaDeviation.rmsDeviation).isEqualTo(0.0)
    }

    @Test
    fun `constructor from JSON handles empty JSON`() {
        val json = JSONObject()

        val diaDeviation = DiaDeviation(json)

        assertThat(diaDeviation.dia).isEqualTo(0.0)
        assertThat(diaDeviation.meanDeviation).isEqualTo(0.0)
        assertThat(diaDeviation.smrDeviation).isEqualTo(0.0)
        assertThat(diaDeviation.rmsDeviation).isEqualTo(0.0)
    }

    @Test
    fun `toJSON includes all fields`() {
        val diaDeviation = DiaDeviation(
            dia = 6.0,
            meanDeviation = 5.5,
            smrDeviation = 3.2,
            rmsDeviation = 4.8
        )

        val json = diaDeviation.toJSON()

        assertThat(json.getDouble("dia")).isEqualTo(6.0)
        assertThat(json.getInt("meanDeviation")).isEqualTo(5)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(3.2)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(4)
    }

    @Test
    fun `toJSON converts meanDeviation to integer`() {
        val diaDeviation = DiaDeviation(meanDeviation = 5.7)

        val json = diaDeviation.toJSON()

        assertThat(json.getInt("meanDeviation")).isEqualTo(5)
    }

    @Test
    fun `toJSON converts rmsDeviation to integer`() {
        val diaDeviation = DiaDeviation(rmsDeviation = 4.9)

        val json = diaDeviation.toJSON()

        assertThat(json.getInt("RMSDeviation")).isEqualTo(4)
    }

    @Test
    fun `toJSON keeps smrDeviation as double`() {
        val diaDeviation = DiaDeviation(smrDeviation = 3.256)

        val json = diaDeviation.toJSON()

        assertThat(json.getDouble("SMRDeviation")).isEqualTo(3.256)
    }

    @Test
    fun `handles zero values`() {
        val diaDeviation = DiaDeviation(
            dia = 0.0,
            meanDeviation = 0.0,
            smrDeviation = 0.0,
            rmsDeviation = 0.0
        )

        val json = diaDeviation.toJSON()

        assertThat(json.getDouble("dia")).isEqualTo(0.0)
        assertThat(json.getInt("meanDeviation")).isEqualTo(0)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(0.0)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(0)
    }

    @Test
    fun `handles negative values`() {
        val diaDeviation = DiaDeviation(
            dia = 5.0,
            meanDeviation = -2.5,
            smrDeviation = -1.3,
            rmsDeviation = -3.7
        )

        val json = diaDeviation.toJSON()

        assertThat(json.getDouble("dia")).isEqualTo(5.0)
        assertThat(json.getInt("meanDeviation")).isEqualTo(-2)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(-1.3)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(-3)
    }

    @Test
    fun `handles large values`() {
        val diaDeviation = DiaDeviation(
            dia = 10.0,
            meanDeviation = 100.0,
            smrDeviation = 50.0,
            rmsDeviation = 75.0
        )

        val json = diaDeviation.toJSON()

        assertThat(json.getDouble("dia")).isEqualTo(10.0)
        assertThat(json.getInt("meanDeviation")).isEqualTo(100)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(50.0)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(75)
    }

    @Test
    fun `handles fractional dia values`() {
        val diaDeviation = DiaDeviation(dia = 6.123)

        val json = diaDeviation.toJSON()

        assertThat(json.getDouble("dia")).isEqualTo(6.123)
    }

    @Test
    fun `roundtrip from JSON to object to JSON preserves data`() {
        val originalJson = JSONObject().apply {
            put("dia", 6.5)
            put("meanDeviation", 5.8)
            put("SMRDeviation", 3.4)
            put("RMSDeviation", 4.2)
        }

        val diaDeviation = DiaDeviation(originalJson)
        val newJson = diaDeviation.toJSON()

        assertThat(newJson.getDouble("dia")).isEqualTo(6.5)
        // meanDeviation gets truncated to int
        assertThat(newJson.getInt("meanDeviation")).isEqualTo(5)
        assertThat(newJson.getDouble("SMRDeviation")).isEqualTo(3.4)
        // rmsDeviation gets truncated to int
        assertThat(newJson.getInt("RMSDeviation")).isEqualTo(4)
    }
}
