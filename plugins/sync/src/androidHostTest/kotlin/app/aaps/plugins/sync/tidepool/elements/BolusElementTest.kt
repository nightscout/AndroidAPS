package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
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
class BolusElementTest {

    @Mock lateinit var dateUtil: DateUtil

    private val iCfg = ICfg(insulinLabel = "Fake", insulinEndTime = 9 * 3600 * 1000, insulinPeakTime = 60 * 60 * 1000, concentration = 1.0)

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

    private fun bolus(timestamp: Long, amount: Double, type: BS.Type): BS =
        BS(timestamp = timestamp, amount = amount, type = type, iCfg = iCfg)

    @Test
    fun `normal bolus sets type bolus and subType normal`() {
        val element = BolusElement(bolus(100, 7.5, BS.Type.NORMAL), dateUtil)

        assertThat(element.type).isEqualTo("bolus")
        assertThat(element.subType).isEqualTo("normal")

        val obj = json(element)
        assertThat(obj.get("type").asString).isEqualTo("bolus")
        assertThat(obj.get("subType").asString).isEqualTo("normal")
    }

    @Test
    fun `normal bolus emits normal and expectedNormal equal to amount`() {
        val element = BolusElement(bolus(100, 7.5, BS.Type.NORMAL), dateUtil)

        assertThat(element.normal).isEqualTo(7.5)
        assertThat(element.expectedNormal).isEqualTo(7.5)

        val obj = json(element)
        assertThat(obj.get("normal").asDouble).isEqualTo(7.5)
        assertThat(obj.get("expectedNormal").asDouble).isEqualTo(7.5)
    }

    @Test
    fun `SMB bolus is marked as automated`() {
        val element = BolusElement(bolus(200, 0.5, BS.Type.SMB), dateUtil)

        assertThat(element.subType).isEqualTo("automated")
        assertThat(element.type).isEqualTo("bolus")

        val obj = json(element)
        assertThat(obj.get("subType").asString).isEqualTo("automated")
        assertThat(obj.get("normal").asDouble).isEqualTo(0.5)
        assertThat(obj.get("expectedNormal").asDouble).isEqualTo(0.5)
    }

    @Test
    fun `PRIMING bolus keeps default subType normal`() {
        // Only BS.Type.SMB switches subType to "automated"; everything else stays "normal".
        val element = BolusElement(bolus(300, 0.3, BS.Type.PRIMING), dateUtil)

        assertThat(element.subType).isEqualTo("normal")
        assertThat(json(element).get("subType").asString).isEqualTo("normal")
    }

    @Test
    fun `base element fields are populated from dateUtil`() {
        val element = BolusElement(bolus(123, 1.0, BS.Type.NORMAL), dateUtil)

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
        val element = BolusElement(bolus(100, 7.5, BS.Type.NORMAL), dateUtil)

        val obj = json(element)
        assertThat(obj.has("origin")).isTrue()
        val origin = obj.getAsJsonObject("origin")
        assertThat(origin.has("id")).isTrue()
        val id = origin.get("id").asString
        assertThat(id).isNotEmpty()

        // Same timestamp -> same id (UUID.nameUUIDFromBytes of "AAPS-bolus" + timestamp).
        val sameTimestamp = BolusElement(bolus(100, 1.0, BS.Type.SMB), dateUtil)
        assertThat(json(sameTimestamp).getAsJsonObject("origin").get("id").asString).isEqualTo(id)

        // Different timestamp -> different id.
        val otherTimestamp = BolusElement(bolus(200, 7.5, BS.Type.NORMAL), dateUtil)
        assertThat(json(otherTimestamp).getAsJsonObject("origin").get("id").asString).isNotEqualTo(id)
    }

    @Test
    fun `emitted json contains exactly the expected exposed keys`() {
        val obj = json(BolusElement(bolus(100, 7.5, BS.Type.NORMAL), dateUtil))

        // Always-present keys (subType, normal, expectedNormal are non-null Double/String -> never omitted).
        assertThat(obj.has("type")).isTrue()
        assertThat(obj.has("subType")).isTrue()
        assertThat(obj.has("normal")).isTrue()
        assertThat(obj.has("expectedNormal")).isTrue()
        assertThat(obj.has("deviceTime")).isTrue()
        assertThat(obj.has("time")).isTrue()
        assertThat(obj.has("timezoneOffset")).isTrue()
        assertThat(obj.has("origin")).isTrue()

        // No extended / dual-wave handling exists in the source -> these keys must be absent.
        assertThat(obj.has("extended")).isFalse()
        assertThat(obj.has("duration")).isFalse()
        assertThat(obj.has("expectedExtended")).isFalse()
        assertThat(obj.has("expectedDuration")).isFalse()
    }

    @Test
    fun `zero amount bolus still emits normal and expectedNormal as zero`() {
        // normal/expectedNormal are non-null primitives, so Gson does NOT omit them even at 0.0.
        val obj = json(BolusElement(bolus(100, 0.0, BS.Type.NORMAL), dateUtil))

        assertThat(obj.has("normal")).isTrue()
        assertThat(obj.has("expectedNormal")).isTrue()
        assertThat(obj.get("normal").asDouble).isEqualTo(0.0)
        assertThat(obj.get("expectedNormal").asDouble).isEqualTo(0.0)
    }
}
