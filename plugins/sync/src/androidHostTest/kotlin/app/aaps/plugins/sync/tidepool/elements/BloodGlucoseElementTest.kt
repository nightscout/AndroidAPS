package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.TimeZone

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloodGlucoseElementTest {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var profileUtil: ProfileUtil

    private var defaultTimeZone: TimeZone = TimeZone.getDefault()

    @BeforeEach
    fun setup() {
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        whenever(dateUtil.toISOAsUTC(any())).thenAnswer { (it.arguments[0] as Long).toString() }
        whenever(dateUtil.toISONoZone(any())).thenAnswer { (it.arguments[0] as Long).toString() }
        whenever(dateUtil.getTimeZoneOffsetMinutes(any())).thenReturn(0)

        // mg/dL values pass through unchanged; mmol/L values are multiplied by 18 (deterministic stub)
        whenever(profileUtil.convertToMgdl(any(), eq(GlucoseUnit.MGDL))).thenAnswer { it.arguments[0] as Double }
        whenever(profileUtil.convertToMgdl(any(), eq(GlucoseUnit.MMOL))).thenAnswer { (it.arguments[0] as Double) * 18.0 }
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private fun te(
        timestamp: Long,
        type: TE.Type,
        glucose: Double?,
        glucoseUnit: GlucoseUnit = GlucoseUnit.MGDL
    ): TE = TE(
        timestamp = timestamp,
        type = type,
        glucose = glucose,
        glucoseUnit = glucoseUnit
    )

    private fun json(e: Any): JsonObject =
        JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(e)).asJsonObject

    private fun jsonArray(e: Any) =
        JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(e)).asJsonArray

    @Test
    fun `fromCareportalEvents emits one element per blood-glucose event`() {
        val events = listOf(
            te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = 120.0),
            te(timestamp = 2000L, type = TE.Type.FINGER_STICK_BG_VALUE, glucose = 95.0)
        )

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `NS_MBG event emits smbg type with manual subType and mg per dL units`() {
        val events = listOf(te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = 120.0))

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)
        assertThat(result).hasSize(1)

        val obj = json(result[0])
        assertThat(obj.get("type").asString).isEqualTo("smbg")
        assertThat(obj.get("subType").asString).isEqualTo("manual")
        assertThat(obj.get("units").asString).isEqualTo("mg/dL")
        assertThat(obj.get("value").asInt).isEqualTo(120)
    }

    @Test
    fun `value is converted to mgdl via profileUtil for mmol input`() {
        // 6.0 mmol/L -> 108 mg/dL via the stubbed conversion (x18)
        val events = listOf(
            te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = 6.0, glucoseUnit = GlucoseUnit.MMOL)
        )

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)
        assertThat(result).hasSize(1)

        val obj = json(result[0])
        assertThat(obj.get("value").asInt).isEqualTo(108)
        assertThat(obj.get("units").asString).isEqualTo("mg/dL")
    }

    @Test
    fun `non blood-glucose events are filtered out`() {
        val events = listOf(
            te(timestamp = 1000L, type = TE.Type.NOTE, glucose = 120.0),
            te(timestamp = 2000L, type = TE.Type.EXERCISE, glucose = 95.0),
            te(timestamp = 3000L, type = TE.Type.SENSOR_CHANGE, glucose = 100.0)
        )

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)

        assertThat(result).isEmpty()
    }

    @Test
    fun `mixed list keeps only blood-glucose events`() {
        val events = listOf(
            te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = 120.0),
            te(timestamp = 2000L, type = TE.Type.NOTE, glucose = 95.0),
            te(timestamp = 3000L, type = TE.Type.FINGER_STICK_BG_VALUE, glucose = 100.0),
            te(timestamp = 4000L, type = TE.Type.SENSOR_CHANGE, glucose = 88.0)
        )

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)

        assertThat(result).hasSize(2)
        val values = result.map { json(it).get("value").asInt }
        assertThat(values).containsExactly(120, 100)
    }

    @Test
    fun `event with null glucose is filtered out because value is not positive`() {
        val events = listOf(te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = null))

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)

        assertThat(result).isEmpty()
    }

    @Test
    fun `event converting to zero or negative mgdl is filtered out`() {
        val events = listOf(
            te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = 0.0),
            te(timestamp = 2000L, type = TE.Type.FINGER_STICK_BG_VALUE, glucose = -5.0)
        )

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)

        assertThat(result).isEmpty()
    }

    @Test
    fun `empty list yields empty result`() {
        val result = BloodGlucoseElement.fromCareportalEvents(emptyList(), dateUtil, profileUtil)

        assertThat(result).isEmpty()
    }

    @Test
    fun `base element fields are populated from dateUtil`() {
        val events = listOf(te(timestamp = 1500L, type = TE.Type.NS_MBG, glucose = 120.0))

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)
        assertThat(result).hasSize(1)

        val obj = json(result[0])
        assertThat(obj.get("time").asString).isEqualTo("1500")
        assertThat(obj.get("deviceTime").asString).isEqualTo("1500")
        assertThat(obj.get("timezoneOffset").asInt).isEqualTo(0)
        assertThat(obj.getAsJsonObject("origin").get("id").asString).isNotEmpty()
    }

    @Test
    fun `serialized list contains all expected blood-glucose elements as json array`() {
        val events = listOf(
            te(timestamp = 1000L, type = TE.Type.NS_MBG, glucose = 120.0),
            te(timestamp = 2000L, type = TE.Type.FINGER_STICK_BG_VALUE, glucose = 95.0)
        )

        val result = BloodGlucoseElement.fromCareportalEvents(events, dateUtil, profileUtil)
        val array = jsonArray(result)

        assertThat(array.size()).isEqualTo(2)
        assertThat(array[0].asJsonObject.get("type").asString).isEqualTo("smbg")
        assertThat(array[1].asJsonObject.get("type").asString).isEqualTo("smbg")
    }
}
