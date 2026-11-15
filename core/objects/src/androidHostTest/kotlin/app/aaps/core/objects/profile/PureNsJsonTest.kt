package app.aaps.core.objects.profile

import android.content.Context
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.TestPumpPlugin
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Pins the shape of the Nightscout profile document produced by `toPureNsJson`.
 *
 * Nothing covered this before: the sync tests call it only to build fixtures, and the one assertion
 * in [ProfileSealedTest] is commented out. That is thin for a serializer whose output is written to
 * the `profileJson` column, uploaded to Nightscout and read back by other AAPS builds - a change in
 * the entry shape would surface as a wrong profile, not as a failing test.
 *
 * The five schedules are produced by one shared helper now, so the per-schedule assertions here are
 * mostly about the boundaries each one walks.
 */
class PureNsJsonTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context

    /** Two blocks per schedule, switching at 02:00, so a boundary error cannot hide. */
    private val sourceProfile =
        """{"dia":"5","carbratio":[{"time":"00:00","value":"30"},{"time":"02:00","value":"40"}],
            "carbs_hr":"20","delay":"20","sens":[{"time":"00:00","value":"100"},{"time":"02:00","value":"110"}],
            "timezone":"UTC","basal":[{"time":"00:00","value":"0.1"},{"time":"02:00","value":"0.2"}],
            "target_low":[{"time":"00:00","value":"4"},{"time":"02:00","value":"4.5"}],
            "target_high":[{"time":"00:00","value":"5"},{"time":"02:00","value":"5.5"}],
            "startDate":"1970-01-01T00:00:00.000Z","units":"mmol"}"""

    private lateinit var dateUtil: DateUtilImpl

    @BeforeEach fun prepare() {
        dateUtil = DateUtilImpl(context)
        whenever(activePlugin.activePump).thenReturn(TestPumpPlugin(rh))
    }

    private fun json() =
        ProfileSealed.Pure(pureProfileFromJson(JSONObject(sourceProfile), dateUtil)!!, activePlugin).toPureNsJson(dateUtil)

    @Test
    fun `carries the five schedules plus units and timezone`() {
        assertThat(json().keys).containsExactly("units", "timezone", "sens", "carbratio", "basal", "target_low", "target_high")
    }

    @Test
    fun `each entry is time, timeAsSeconds and value`() {
        val first = json()["sens"]!!.jsonArray[0].jsonObject

        assertThat(first.keys).containsExactly("time", "timeAsSeconds", "value")
        assertThat(first["time"]!!.jsonPrimitive.content).isEqualTo("00:00")
        assertThat(first["timeAsSeconds"]!!.jsonPrimitive.content).isEqualTo("0")
    }

    @Test
    fun `every schedule walks its own block boundaries`() {
        val o = json()
        for (key in listOf("sens", "carbratio", "basal", "target_low", "target_high")) {
            val entries = o[key]!!.jsonArray
            assertThat(entries).hasSize(2)
            assertThat(entries[0].jsonObject["time"]!!.jsonPrimitive.content).isEqualTo("00:00")
            assertThat(entries[1].jsonObject["time"]!!.jsonPrimitive.content).isEqualTo("02:00")
            assertThat(entries[1].jsonObject["timeAsSeconds"]!!.jsonPrimitive.content).isEqualTo("7200")
        }
    }

    @Test
    fun `values come through per schedule`() {
        val o = json()
        fun valueAt(key: String, index: Int) = o[key]!!.jsonArray[index].jsonObject["value"]!!.jsonPrimitive.content.toDouble()

        assertThat(valueAt("sens", 0)).isWithin(0.001).of(100.0)
        assertThat(valueAt("sens", 1)).isWithin(0.001).of(110.0)
        assertThat(valueAt("carbratio", 1)).isWithin(0.001).of(40.0)
        assertThat(valueAt("basal", 1)).isWithin(0.001).of(0.2)
        // Low and high share boundaries but not values.
        assertThat(valueAt("target_low", 1)).isWithin(0.001).of(4.5)
        assertThat(valueAt("target_high", 1)).isWithin(0.001).of(5.5)
    }

    /**
     * The document round-trips back into the profile it came from.
     *
     * This is the assertion that actually matters: whatever the text looks like, another AAPS build
     * reading it must rebuild the same blocks. It is also what makes the one cosmetic difference from
     * the previous `org.json` output harmless - integral values now render as `100.0` rather than
     * `100`, and the reader does not care.
     */
    @Test
    fun `the document parses back into the same blocks`() {
        val original = ProfileSealed.Pure(pureProfileFromJson(JSONObject(sourceProfile), dateUtil)!!, activePlugin)

        val reparsed = pureProfileFromJson(JSONObject(original.toPureNsJson(dateUtil).toString()), dateUtil)!!

        assertThat(reparsed.isfBlocks).isEqualTo(original.isfBlocks)
        assertThat(reparsed.icBlocks).isEqualTo(original.icBlocks)
        assertThat(reparsed.basalBlocks).isEqualTo(original.basalBlocks)
        assertThat(reparsed.targetBlocks).isEqualTo(original.targetBlocks)
    }
}
