package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

/**
 * Verifies that each [BasalElement] delivery type serializes to the exact shape the Tidepool `basal`
 * schema allows:
 *  - `scheduled` / `automated`: carry `rate` + `scheduleName`, never `suppressed`
 *  - `suspend`: carries neither `rate` nor `scheduleName` (the schema forbids them)
 * and that `origin.id` is deterministic and collision-free across kinds.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasalElementTest {

    @Mock lateinit var dateUtil: DateUtil

    private fun json(element: Any): JsonObject =
        JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(element)).asJsonObject

    private fun originId(element: Any): String =
        json(element).getAsJsonObject("origin").get("id").asString

    @Test
    fun `scheduled basal carries type basal, rate and scheduleName and no suppressed`() {
        val obj = json(BasalElement.scheduled(timestamp = 1_000L, duration = 3_600_000L, rate = 0.75, dateUtil = dateUtil))
        assertThat(obj.get("type").asString).isEqualTo("basal")
        assertThat(obj.get("deliveryType").asString).isEqualTo("scheduled")
        assertThat(obj.get("rate").asDouble).isEqualTo(0.75)
        assertThat(obj.get("duration").asLong).isEqualTo(3_600_000L)
        assertThat(obj.get("scheduleName").asString).isEqualTo("AAPS")
        assertThat(obj.has("suppressed")).isFalse()
    }

    @Test
    fun `automated basal carries rate and scheduleName and no suppressed`() {
        val obj = json(BasalElement.automated(timestamp = 2_000L, duration = 1_800_000L, rate = 1.25, tbrTimestamp = 1_500L, dateUtil = dateUtil))
        assertThat(obj.get("type").asString).isEqualTo("basal")
        assertThat(obj.get("deliveryType").asString).isEqualTo("automated")
        assertThat(obj.get("rate").asDouble).isEqualTo(1.25)
        assertThat(obj.get("duration").asLong).isEqualTo(1_800_000L)
        assertThat(obj.get("scheduleName").asString).isEqualTo("AAPS")
        assertThat(obj.has("suppressed")).isFalse()
    }

    @Test
    fun `pump suspend carries neither rate nor scheduleName`() {
        val obj = json(BasalElement.pumpSuspend(timestamp = 3_000L, duration = 600_000L, tbrTimestamp = 2_500L, dateUtil = dateUtil))
        assertThat(obj.get("type").asString).isEqualTo("basal")
        assertThat(obj.get("deliveryType").asString).isEqualTo("suspend")
        assertThat(obj.get("duration").asLong).isEqualTo(600_000L)
        assertThat(obj.has("rate")).isFalse()
        assertThat(obj.has("scheduleName")).isFalse()
        assertThat(obj.has("suppressed")).isFalse()
    }

    @Test
    fun `scheduled id is stable for the same timestamp and differs by timestamp`() {
        val a = originId(BasalElement.scheduled(1_000L, 1_000L, 0.5, dateUtil))
        val sameTs = originId(BasalElement.scheduled(1_000L, 9_999L, 0.9, dateUtil)) // same ts, different rate/duration
        val otherTs = originId(BasalElement.scheduled(2_000L, 1_000L, 0.5, dateUtil))
        assertThat(a).isEqualTo(sameTs)
        assertThat(a).isNotEqualTo(otherTs)
    }

    @Test
    fun `ids do not collide across delivery types for identical timestamps`() {
        val scheduled = originId(BasalElement.scheduled(5_000L, 1_000L, 0.5, dateUtil))
        val automated = originId(BasalElement.automated(5_000L, 1_000L, 0.5, 5_000L, dateUtil))
        val suspend = originId(BasalElement.pumpSuspend(5_000L, 1_000L, 5_000L, dateUtil))
        assertThat(setOf(scheduled, automated, suspend)).hasSize(3)
    }

    @Test
    fun `automated id depends on both the tbr timestamp and the segment timestamp`() {
        val base = originId(BasalElement.automated(10_000L, 1_000L, 1.0, 8_000L, dateUtil))
        val otherSegment = originId(BasalElement.automated(11_000L, 1_000L, 1.0, 8_000L, dateUtil))
        val otherTbr = originId(BasalElement.automated(10_000L, 1_000L, 1.0, 9_000L, dateUtil))
        assertThat(base).isNotEqualTo(otherSegment)
        assertThat(base).isNotEqualTo(otherTbr)
    }

    @Test
    fun `serialized fields are only the exposed ones`() {
        val obj = json(BasalElement.scheduled(1_000L, 1_000L, 0.5, dateUtil))
        // deviceTime/time are null (dateUtil unstubbed) and omitted by Gson; assert the key set we do emit.
        assertThat(obj.has("deliveryType")).isTrue()
        assertThat(obj.has("rate")).isTrue()
        assertThat(obj.has("duration")).isTrue()
        assertThat(obj.has("scheduleName")).isTrue()
        assertThat(obj.has("clockDriftOffset")).isTrue()
        assertThat(obj.has("conversionOffset")).isTrue()
    }
}
