package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.CA
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
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WizardElementTest {

    @Mock lateinit var dateUtil: DateUtil

    private var originalTimeZone: TimeZone = TimeZone.getDefault()

    private val iCfg = ICfg(insulinLabel = "Fake", insulinEndTime = 9 * 3600 * 1000, insulinPeakTime = 60 * 60 * 1000, concentration = 1.0)

    @BeforeEach
    fun setup() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // toISOAsUTC / toISONoZone simply echo the Long timestamp as a String so we can assert on it
        whenever(dateUtil.toISOAsUTC(any())).thenAnswer { (it.arguments[0] as Long).toString() }
        whenever(dateUtil.toISONoZone(any())).thenAnswer { (it.arguments[0] as Long).toString() }
        whenever(dateUtil.getTimeZoneOffsetMinutes(any())).thenReturn(0)
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun json(e: Any): JsonObject = JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(e)).asJsonObject

    private fun carbs(timestamp: Long = 1_000_000L, amount: Double = 57.0, duration: Long = 0L): CA =
        CA(timestamp = timestamp, duration = duration, amount = amount)

    @Test
    fun `type is wizard`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        assertThat(json(element).get("type").asString).isEqualTo("wizard")
    }

    @Test
    fun `carbInput equals carb amount`() {
        val element = WizardElement(carbs(amount = 57.0), dateUtil, iCfg)
        assertThat(json(element).get("carbInput").asDouble).isEqualTo(57.0)
    }

    @Test
    fun `carbInput reflects a different carb amount`() {
        val element = WizardElement(carbs(amount = 12.5), dateUtil, iCfg)
        assertThat(json(element).get("carbInput").asDouble).isEqualTo(12.5)
    }

    @Test
    fun `units is mg per dL`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        assertThat(json(element).get("units").asString).isEqualTo("mg/dL")
    }

    @Test
    fun `insulinCarbRatio defaults to zero`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        assertThat(json(element).get("insulinCarbRatio").asDouble).isEqualTo(0.0)
    }

    @Test
    fun `time uses toISOAsUTC of carb timestamp`() {
        val element = WizardElement(carbs(timestamp = 1_000_000L), dateUtil, iCfg)
        assertThat(json(element).get("time").asString).isEqualTo("1000000")
    }

    @Test
    fun `deviceTime uses toISONoZone of carb timestamp`() {
        val element = WizardElement(carbs(timestamp = 1_000_000L), dateUtil, iCfg)
        assertThat(json(element).get("deviceTime").asString).isEqualTo("1000000")
    }

    @Test
    fun `timezoneOffset uses dateUtil getTimeZoneOffsetMinutes`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        assertThat(json(element).get("timezoneOffset").asInt).isEqualTo(0)
    }

    @Test
    fun `origin id is the name based UUID derived from AAPS-wizard prefix and timestamp`() {
        val timestamp = 1_000_000L
        val expectedId = UUID.nameUUIDFromBytes(("AAPS-wizard$timestamp").toByteArray()).toString()
        val element = WizardElement(carbs(timestamp = timestamp), dateUtil, iCfg)
        val origin = json(element).getAsJsonObject("origin")
        assertThat(origin.get("id").asString).isEqualTo(expectedId)
    }

    @Test
    fun `bolus is a nested element with type bolus`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        val bolus = json(element).getAsJsonObject("bolus")
        assertThat(bolus.get("type").asString).isEqualTo("bolus")
    }

    @Test
    fun `nested bolus normal and expectedNormal carry the fake amount`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        val bolus = json(element).getAsJsonObject("bolus")
        // The wizard builds a fake BS with amount = 0.0001
        assertThat(bolus.get("normal").asDouble).isEqualTo(0.0001)
        assertThat(bolus.get("expectedNormal").asDouble).isEqualTo(0.0001)
    }

    @Test
    fun `nested bolus subType is normal because fake bolus type is NORMAL`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        val bolus = json(element).getAsJsonObject("bolus")
        assertThat(bolus.get("subType").asString).isEqualTo("normal")
    }

    @Test
    fun `nested bolus shares the carb timestamp for its time fields`() {
        val timestamp = 2_500_000L
        val element = WizardElement(carbs(timestamp = timestamp), dateUtil, iCfg)
        val bolus = json(element).getAsJsonObject("bolus")
        assertThat(bolus.get("time").asString).isEqualTo("2500000")
        assertThat(bolus.get("deviceTime").asString).isEqualTo("2500000")
    }

    @Test
    fun `nested bolus origin id uses AAPS-bolus prefix and the carb timestamp`() {
        val timestamp = 2_500_000L
        val expectedId = UUID.nameUUIDFromBytes(("AAPS-bolus$timestamp").toByteArray()).toString()
        val element = WizardElement(carbs(timestamp = timestamp), dateUtil, iCfg)
        val origin = json(element).getAsJsonObject("bolus").getAsJsonObject("origin")
        assertThat(origin.get("id").asString).isEqualTo(expectedId)
    }

    @Test
    fun `emitted keys are exactly the exposed fields and nothing more`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        val keys = json(element).keySet()
        assertThat(keys).containsExactly(
            // BaseElement exposed fields
            "deviceTime", "time", "timezoneOffset", "type", "origin",
            // WizardElement exposed fields
            "units", "carbInput", "insulinCarbRatio", "bolus"
        )
    }

    @Test
    fun `nested bolus emits exactly its exposed fields`() {
        val element = WizardElement(carbs(), dateUtil, iCfg)
        val keys = json(element).getAsJsonObject("bolus").keySet()
        assertThat(keys).containsExactly(
            // BaseElement exposed fields
            "deviceTime", "time", "timezoneOffset", "type", "origin",
            // BolusElement exposed fields
            "subType", "normal", "expectedNormal"
        )
    }
}
