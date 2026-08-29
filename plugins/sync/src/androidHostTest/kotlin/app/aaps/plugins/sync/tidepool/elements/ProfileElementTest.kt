package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.TimeZone

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileElementTest {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var profileUtil: ProfileUtil

    // Full-day single blocks (duration = 24h in ms). A single block keeps the 0..23 loop in
    // ProfileElement total and avoids any block-boundary edge cases.
    private val fullDay = 24L * 60L * 60L * 1000L // 86_400_000

    private val basalRate = 0.75
    private val isfMgdl = 50.0
    private val icRatio = 10.0
    private val lowTargetMgdl = 100.0
    private val highTargetMgdl = 120.0

    private val timestamp = 1_000_000L
    private val serialNumber = "SN-12345"

    private var defaultTz: TimeZone? = null

    private fun buildEps(): EPS =
        EPS(
            timestamp = timestamp,
            basalBlocks = listOf(Block(fullDay, basalRate)),
            isfBlocks = listOf(Block(fullDay, isfMgdl)),
            icBlocks = listOf(Block(fullDay, icRatio)),
            targetBlocks = listOf(TargetBlock(fullDay, lowTargetMgdl, highTargetMgdl)),
            glucoseUnit = GlucoseUnit.MGDL,
            originalProfileName = "TestProfile",
            originalCustomizedName = "TestProfile",
            originalTimeshift = 0,
            originalPercentage = 100,
            originalDuration = 0,
            originalEnd = 0,
            iCfg = ICfg(insulinLabel = "Fake", insulinEndTime = 9 * 3600 * 1000, insulinPeakTime = 60 * 60 * 1000, concentration = 1.0)
        )

    private fun json(e: Any): JsonObject =
        JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(e)).asJsonObject

    @BeforeEach
    fun setup() {
        defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        whenever(dateUtil.toISOAsUTC(any())).thenAnswer { (it.arguments[0] as Long).toString() }
        whenever(dateUtil.toISONoZone(any())).thenAnswer { (it.arguments[0] as Long).toString() }
        whenever(dateUtil.getTimeZoneOffsetMinutes(any())).thenReturn(0)
        // ProfileElement calls profileUtil.convertToMgdlDetect(<Double>); echo the argument back.
        whenever(profileUtil.convertToMgdlDetect(any())).thenAnswer { it.arguments[0] as Double }
    }

    @AfterEach
    fun tearDown() {
        defaultTz?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun `type is pumpSettings`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        assertThat(json(sut).get("type").asString).isEqualTo("pumpSettings")
    }

    @Test
    fun `activeSchedule is Normal`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        assertThat(json(sut).get("activeSchedule").asString).isEqualTo("Normal")
    }

    @Test
    fun `basalSchedules has Normal array with 24 entries carrying start and rate`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val normal = json(sut).getAsJsonObject("basalSchedules").getAsJsonArray("Normal")
        assertThat(normal.size()).isEqualTo(24)
        for (hour in 0..23) {
            val entry = normal[hour].asJsonObject
            assertThat(entry.get("start").asInt).isEqualTo(hour * 3600 * 1000)
            assertThat(entry.get("rate").asDouble).isEqualTo(basalRate)
        }
    }

    @Test
    fun `basal rate matches the configured block amount`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val normal = json(sut).getAsJsonObject("basalSchedules").getAsJsonArray("Normal")
        // percentage forced to 100 in ProfileSealed.EPS, so emitted rate == block amount
        assertThat(normal[0].asJsonObject.get("rate").asDouble).isEqualTo(basalRate)
        assertThat(normal[23].asJsonObject.get("rate").asDouble).isEqualTo(basalRate)
    }

    @Test
    fun `units block reports grams and mg per dL`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val units = json(sut).getAsJsonObject("units")
        assertThat(units.get("carb").asString).isEqualTo("grams")
        assertThat(units.get("bg").asString).isEqualTo("mg/dL")
    }

    @Test
    fun `bgTargets has Normal array of 24 entries with start and target`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val normal = json(sut).getAsJsonObject("bgTargets").getAsJsonArray("Normal")
        assertThat(normal.size()).isEqualTo(24)
        for (hour in 0..23) {
            val entry = normal[hour].asJsonObject
            assertThat(entry.get("start").asInt).isEqualTo(hour * 3600 * 1000)
            // ProfileElement averages low with itself: (low + low) / 2 == low, echoed by stub, truncated to Int
            assertThat(entry.get("target").asInt).isEqualTo(lowTargetMgdl.toInt())
        }
    }

    @Test
    fun `carbRatios has Normal array of 24 entries with start and amount`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val normal = json(sut).getAsJsonObject("carbRatios").getAsJsonArray("Normal")
        assertThat(normal.size()).isEqualTo(24)
        for (hour in 0..23) {
            val entry = normal[hour].asJsonObject
            assertThat(entry.get("start").asInt).isEqualTo(hour * 3600 * 1000)
            assertThat(entry.get("amount").asInt).isEqualTo(icRatio.toInt())
        }
    }

    @Test
    fun `insulinSensitivities has Normal array of 24 entries with start and amount`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val normal = json(sut).getAsJsonObject("insulinSensitivities").getAsJsonArray("Normal")
        assertThat(normal.size()).isEqualTo(24)
        for (hour in 0..23) {
            val entry = normal[hour].asJsonObject
            assertThat(entry.get("start").asInt).isEqualTo(hour * 3600 * 1000)
            assertThat(entry.get("amount").asInt).isEqualTo(isfMgdl.toInt())
        }
    }

    @Test
    fun `deviceId and deviceSerialNumber derive from serialNumber argument`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val o = json(sut)
        assertThat(o.get("deviceSerialNumber").asString).isEqualTo(serialNumber)
        assertThat(o.get("deviceId").asString).endsWith(serialNumber)
        assertThat(o.get("deviceId").asString).contains(":$serialNumber")
    }

    @Test
    fun `base element time fields populated from dateUtil`() {
        val sut = ProfileElement(buildEps(), serialNumber, dateUtil, profileUtil)
        val o = json(sut)
        assertThat(o.get("time").asString).isEqualTo(timestamp.toString())
        assertThat(o.get("deviceTime").asString).isEqualTo(timestamp.toString())
        assertThat(o.get("timezoneOffset").asInt).isEqualTo(0)
        assertThat(o.getAsJsonObject("origin").get("id").asString).isNotEmpty()
    }
}
