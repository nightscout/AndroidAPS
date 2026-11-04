package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * Holds the kotlinx replacement to what Gson used to write.
 *
 * The bolus wizard breakdown is stored in Nightscout and read back by builds on both sides of this
 * change, so the text has to stay compatible in both directions. These tests use the real [bcrJson]
 * from production, not a copy of its settings - a copy would prove nothing.
 */
class BolusCalculatorResultJsonCompatTest {

    private val gson = Gson()

    /** Every field populated, including a pump type enum and a null inside [IDs]. */
    private fun sample() = BCR(
        id = 7,
        version = 3,
        dateCreated = 1_700_000_000_000,
        isValid = true,
        referenceId = null,
        ids = IDs(nightscoutId = "ns-1", pumpType = PumpType.OMNIPOD_DASH, pumpSerial = "abc", pumpId = 42),
        timestamp = 1_700_000_001_000,
        utcOffset = 3_600_000,
        targetBGLow = 5.5, targetBGHigh = 6.5, isf = 2.5, ic = 10.0,
        bolusIOB = -0.25, wasBolusIOBUsed = true,
        basalIOB = 0.5, wasBasalIOBUsed = false,
        glucoseValue = 7.7, wasGlucoseUsed = true,
        glucoseDifference = 1.2, glucoseInsulin = 0.4, glucoseTrend = -0.3, wasTrendUsed = false,
        trendInsulin = 0.1, cob = 12.0, wasCOBUsed = true, cobInsulin = 1.1,
        carbs = 30.0, wereCarbsUsed = true, carbsInsulin = 3.0,
        otherCorrection = 0.2, wasSuperbolusUsed = false, superbolusInsulin = 0.0,
        wasTempTargetUsed = false, totalInsulin = 4.8, percentageCorrection = 90,
        profileName = "Default", note = "a note"
    )

    @Test
    fun `kotlinx reads a record that Gson wrote`() {
        val original = sample()

        val fromGson = gson.toJson(original)
        val decoded = bcrJson.decodeFromString<BCR>(fromGson)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `Gson reads a record that kotlinx wrote`() {
        val original = sample()

        val fromKotlinx = bcrJson.encodeToString(original)
        val decoded = gson.fromJson(fromKotlinx, BCR::class.java)

        assertThat(decoded).isEqualTo(original)
    }

    /**
     * The failure that would not show up as an exception. Gson fills a missing field with the JVM
     * zero value, so an omitted `isValid` reads back as false and a good record silently turns
     * invalid. kotlinx omits defaults unless told otherwise, which is why [bcrJson] sets
     * `encodeDefaults`.
     */
    @Test
    fun `fields sitting at their default value are still written out`() {
        // isValid defaults to true, id and version to 0 - all three would vanish without encodeDefaults.
        val atDefaults = sample().apply { isValid = true; id = 0; version = 0 }

        val written = JSONObject(bcrJson.encodeToString(atDefaults))

        assertThat(written.has("isValid")).isTrue()
        assertThat(written.getBoolean("isValid")).isTrue()
        assertThat(written.has("id")).isTrue()
        assertThat(written.has("version")).isTrue()
    }

    /** Gson leaves nulls out entirely. Writing `"referenceId": null` instead would be new text. */
    @Test
    fun `null fields are left out, the way Gson left them out`() {
        val withNull = sample().also { it.referenceId = null }

        val kotlinxText = JSONObject(bcrJson.encodeToString(withNull))
        val gsonText = JSONObject(gson.toJson(withNull))

        assertThat(kotlinxText.has("referenceId")).isFalse()
        assertThat(gsonText.has("referenceId")).isFalse()
    }

    /** A record from a newer build carrying a field this one does not know must still load. */
    @Test
    fun `an unknown field does not stop a record loading`() {
        val text = JSONObject(gson.toJson(sample())).put("fieldFromANewerBuild", 1).toString()

        val decoded = bcrJson.decodeFromString<BCR>(text)

        assertThat(decoded).isEqualTo(sample())
    }

    @Test
    fun `the pump type enum survives a round trip through both writers`() {
        val original = sample()

        assertThat(bcrJson.decodeFromString<BCR>(gson.toJson(original)).ids.pumpType)
            .isEqualTo(PumpType.OMNIPOD_DASH)
        assertThat(gson.fromJson(bcrJson.encodeToString(original), BCR::class.java).ids.pumpType)
            .isEqualTo(PumpType.OMNIPOD_DASH)
    }
}
