package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.DS
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [DS] toJson: nested pump/openaps/configuration JSON embedding and the null/zero omissions. */
class DeviceStatusExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
    }

    @Test
    fun mapsNestedJsonFields() {
        val ds = DS(
            timestamp = 1000L, uploaderBattery = 85, isCharging = true, device = "openaps://AAPS",
            pump = """{"battery":{"percent":90}}""",
            enacted = """{"rate":1.2}""",
            suggested = """{"bg":120}""",
            iob = """{"iob":0.5}""",
            configuration = """{"version":"3.0"}"""
        )
        val json = ds.toJson(dateUtil)
        assertThat(json.getString("created_at")).isEqualTo("2023-01-01T00:00:00Z")
        assertThat(json.getString("device")).isEqualTo("openaps://AAPS")
        assertThat(json.getJSONObject("pump").getJSONObject("battery").getInt("percent")).isEqualTo(90)
        assertThat(json.getInt("uploaderBattery")).isEqualTo(85)
        assertThat(json.getBoolean("isCharging")).isTrue()
        val openaps = json.getJSONObject("openaps")
        assertThat(openaps.getJSONObject("enacted").getDouble("rate")).isEqualTo(1.2)
        assertThat(openaps.getJSONObject("suggested").getInt("bg")).isEqualTo(120)
        assertThat(openaps.getJSONObject("iob").getDouble("iob")).isEqualTo(0.5)
        assertThat(json.getJSONObject("configuration").getString("version")).isEqualTo("3.0")
    }

    @Test
    fun omitsOptionalWhenNullOrZero() {
        val ds = DS(timestamp = 1000L, uploaderBattery = 0, isCharging = null)
        val json = ds.toJson(dateUtil)
        assertThat(json.has("device")).isFalse()
        assertThat(json.has("pump")).isFalse()
        assertThat(json.has("uploaderBattery")).isFalse() // zero omitted
        assertThat(json.has("isCharging")).isFalse()
        assertThat(json.has("configuration")).isFalse()
        assertThat(json.has("openaps")).isTrue() // openaps block always present
    }
}
