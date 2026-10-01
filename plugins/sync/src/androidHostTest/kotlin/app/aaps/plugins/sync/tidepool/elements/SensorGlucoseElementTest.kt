package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.TimeZone

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensorGlucoseElementTest {

    @Mock lateinit var dateUtil: DateUtil

    private lateinit var savedTimeZone: TimeZone

    @BeforeEach
    fun setup() {
        savedTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        whenever(dateUtil.toISOAsUTC(any())).thenAnswer { it.getArgument<Long>(0).toString() }
        whenever(dateUtil.toISONoZone(any())).thenAnswer { it.getArgument<Long>(0).toString() }
        whenever(dateUtil.getTimeZoneOffsetMinutes(any())).thenReturn(0)
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(savedTimeZone)
    }

    private fun json(e: Any): JsonObject = JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(e)).asJsonObject

    private fun bgReading(timestamp: Long, value: Double): GV =
        GV(
            timestamp = timestamp,
            raw = null,
            value = value,
            trendArrow = TrendArrow.FLAT,
            noise = null,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE
        )

    @Test
    fun `single element has type cbg and units mgdl`() {
        // The source hardcodes units = "mg/dL" and type = "cbg"; value is bgReading.value.toInt() (NO mmol/L conversion).
        val element = SensorGlucoseElement(bgReading(100, 120.0), dateUtil)

        assertThat(element.type).isEqualTo("cbg")
        assertThat(element.units).isEqualTo("mg/dL")
        assertThat(element.value).isEqualTo(120)

        val obj = json(element)
        assertThat(obj.get("type").asString).isEqualTo("cbg")
        assertThat(obj.get("units").asString).isEqualTo("mg/dL")
        assertThat(obj.get("value").asInt).isEqualTo(120)
    }

    @Test
    fun `value is truncated to int from the double bg value`() {
        // value = bgReading.value.toInt() -> toInt() truncates toward zero, no rounding.
        assertThat(SensorGlucoseElement(bgReading(100, 99.9), dateUtil).value).isEqualTo(99)
        assertThat(SensorGlucoseElement(bgReading(100, 100.0), dateUtil).value).isEqualTo(100)
        assertThat(SensorGlucoseElement(bgReading(100, 154.7), dateUtil).value).isEqualTo(154)

        val obj = json(SensorGlucoseElement(bgReading(100, 99.9), dateUtil))
        assertThat(obj.get("value").asInt).isEqualTo(99)
    }

    @Test
    fun `value is the raw mgdl number and is not converted to mmol per liter`() {
        // 120 mg/dL would be ~6.7 mmol/L; the source does NOT convert, so the emitted value stays 120.
        val obj = json(SensorGlucoseElement(bgReading(100, 120.0), dateUtil))

        assertThat(obj.get("value").asInt).isEqualTo(120)
        assertThat(obj.get("value").asDouble).isEqualTo(120.0)
        assertThat(obj.get("units").asString).isEqualTo("mg/dL")
    }

    @Test
    fun `base element fields are populated from dateUtil`() {
        val element = SensorGlucoseElement(bgReading(123, 100.0), dateUtil)

        // dateUtil stubs echo the timestamp as String for time/deviceTime, 0 for offset.
        assertThat(element.deviceTime).isEqualTo("123")
        assertThat(element.time).isEqualTo("123")
        assertThat(element.timezoneOffset).isEqualTo(0)

        val obj = json(element)
        assertThat(obj.get("deviceTime").asString).isEqualTo("123")
        assertThat(obj.get("time").asString).isEqualTo("123")
        assertThat(obj.get("timezoneOffset").asInt).isEqualTo(0)
    }

    @Test
    fun `origin id is a deterministic uuid derived from timestamp`() {
        val element = SensorGlucoseElement(bgReading(100, 120.0), dateUtil)

        val obj = json(element)
        assertThat(obj.has("origin")).isTrue()
        val origin = obj.getAsJsonObject("origin")
        assertThat(origin.has("id")).isTrue()
        val id = origin.get("id").asString
        assertThat(id).isNotEmpty()

        // Same timestamp -> same id (UUID.nameUUIDFromBytes of "AAPS-cgm" + timestamp), independent of value.
        val sameTimestamp = SensorGlucoseElement(bgReading(100, 80.0), dateUtil)
        assertThat(json(sameTimestamp).getAsJsonObject("origin").get("id").asString).isEqualTo(id)

        // Different timestamp -> different id.
        val otherTimestamp = SensorGlucoseElement(bgReading(200, 120.0), dateUtil)
        assertThat(json(otherTimestamp).getAsJsonObject("origin").get("id").asString).isNotEqualTo(id)
    }

    @Test
    fun `emitted json contains exactly the expected exposed keys`() {
        val obj = json(SensorGlucoseElement(bgReading(100, 120.0), dateUtil))

        // Always-present keys (units String, value Int are non-null -> never omitted).
        assertThat(obj.has("type")).isTrue()
        assertThat(obj.has("units")).isTrue()
        assertThat(obj.has("value")).isTrue()
        assertThat(obj.has("deviceTime")).isTrue()
        assertThat(obj.has("time")).isTrue()
        assertThat(obj.has("timezoneOffset")).isTrue()
        assertThat(obj.has("origin")).isTrue()

        // No raw / trend / noise / sourceSensor fields are emitted by this element.
        assertThat(obj.has("raw")).isFalse()
        assertThat(obj.has("trend")).isFalse()
        assertThat(obj.has("trendArrow")).isFalse()
        assertThat(obj.has("noise")).isFalse()
        assertThat(obj.has("sourceSensor")).isFalse()
    }

    @Test
    fun `zero bg value still emits value as zero`() {
        // value is a non-null primitive Int, so Gson does NOT omit it even at 0.
        val obj = json(SensorGlucoseElement(bgReading(100, 0.0), dateUtil))

        assertThat(obj.has("value")).isTrue()
        assertThat(obj.get("value").asInt).isEqualTo(0)
        assertThat(obj.get("units").asString).isEqualTo("mg/dL")
    }

    @Test
    fun `fromBgReadings maps each reading to a cbg element preserving order`() {
        val readings = listOf(
            bgReading(100, 80.0),
            bgReading(200, 120.0),
            bgReading(300, 200.0)
        )

        val elements = SensorGlucoseElement.fromBgReadings(readings, dateUtil)

        assertThat(elements).hasSize(3)
        assertThat(elements.map { it.value }).containsExactly(80, 120, 200).inOrder()
        assertThat(elements.map { it.type }).containsExactly("cbg", "cbg", "cbg").inOrder()
        assertThat(elements.map { it.units }).containsExactly("mg/dL", "mg/dL", "mg/dL").inOrder()
        assertThat(elements.map { it.time }).containsExactly("100", "200", "300").inOrder()
    }

    @Test
    fun `fromBgReadings serialized list emits one cbg object per reading`() {
        val readings = listOf(
            bgReading(100, 80.0),
            bgReading(200, 120.0)
        )

        val elements = SensorGlucoseElement.fromBgReadings(readings, dateUtil)
        val arrayJson = GsonInstance.defaultGsonInstance().toJson(elements)
        val array = JsonParser.parseString(arrayJson).asJsonArray

        assertThat(array.size()).isEqualTo(2)

        val first = array[0].asJsonObject
        assertThat(first.get("type").asString).isEqualTo("cbg")
        assertThat(first.get("units").asString).isEqualTo("mg/dL")
        assertThat(first.get("value").asInt).isEqualTo(80)
        assertThat(first.get("time").asString).isEqualTo("100")

        val second = array[1].asJsonObject
        assertThat(second.get("type").asString).isEqualTo("cbg")
        assertThat(second.get("units").asString).isEqualTo("mg/dL")
        assertThat(second.get("value").asInt).isEqualTo(120)
        assertThat(second.get("time").asString).isEqualTo("200")
    }

    @Test
    fun `fromBgReadings with empty list returns empty result`() {
        val elements = SensorGlucoseElement.fromBgReadings(emptyList(), dateUtil)

        assertThat(elements).isEmpty()

        val array = JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(elements)).asJsonArray
        assertThat(array.size()).isEqualTo(0)
    }
}
