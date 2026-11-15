package app.aaps.core.objects.extensions

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.json.JSONArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

/**
 * Pins the block → JSON renderers.
 *
 * These author two formats AAPS does not own alone: the profile document that master and client
 * exchange (and that a 3.4.x build must still be able to read), and the Nightscout profile store. A
 * renderer that drifted from [blockFromJson] would not fail loudly — profiles would round-trip to
 * slightly different times or values, and the first sign would be a wrong basal rate.
 *
 * So the central property here is **round-trip identity**: rendering a schedule and parsing it back
 * must return the same blocks, for every schedule the parser accepts.
 */
class BlockRenderTest {

    /**
     * `org.json` fixtures, kotlinx readers - the adapters these used to call are gone with their
     * last production caller. Kept here because a stored schedule still arrives as `org.json`.
     */
    private fun JSONArray?.kx(): JsonArray? =
        this?.let { Json.parseToJsonElement(it.toString()) as? JsonArray }

    private fun List<Block>.toJSONArray(): JSONArray = JSONArray(toJsonArray().toString())
    private fun List<TargetBlock>.lowToJSONArray(): JSONArray = JSONArray(lowToJsonArray().toString())
    private fun List<TargetBlock>.highToJSONArray(): JSONArray = JSONArray(highToJsonArray().toString())

    private fun blockFromJsonArray(jsonArray: JSONArray?, dateUtil: DateUtil) =
        blockFromJson(jsonArray.kx(), dateUtil)

    private fun targetBlockFromJsonArray(low: JSONArray?, high: JSONArray?, dateUtil: DateUtil) =
        targetBlockFromJson(low.kx(), high.kx(), dateUtil)

    private val dateUtil: DateUtil = mock()
    private lateinit var originalLocale: Locale

    @BeforeEach fun setUp() {
        // toSeconds() is the real parser's job; here it only has to agree with what we render.
        whenever(dateUtil.toSeconds(org.mockito.kotlin.any())).thenAnswer { invocation ->
            val text = invocation.getArgument<String>(0)
            text.substringBefore(':').toInt() * 3600 + text.substringAfter(':').toInt() * 60
        }
        originalLocale = Locale.getDefault()
    }

    @AfterEach fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private fun blocks(vararg hoursAndValue: Pair<Int, Double>): List<Block> =
        hoursAndValue.map { (hours, value) -> Block(T.hours(hours.toLong()).msecs(), value) }

    private fun targets(vararg hoursLowHigh: Triple<Int, Double, Double>): List<TargetBlock> =
        hoursLowHigh.map { (hours, low, high) -> TargetBlock(T.hours(hours.toLong()).msecs(), low, high) }

    @Test
    fun `a whole-day single block renders as one 00-00 entry`() {
        val json = singleBlock(0.1).toJSONArray()

        assertThat(json.length()).isEqualTo(1)
        assertThat(json.getJSONObject(0).getString("time")).isEqualTo("00:00")
        assertThat(json.getJSONObject(0).getInt("timeAsSeconds")).isEqualTo(0)
        assertThat(json.getJSONObject(0).getDouble("value")).isEqualTo(0.1)
    }

    @Test
    fun `start times are the running sum of the durations before them`() {
        val json = blocks(1 to 1.0, 2 to 2.0, 21 to 3.0).toJSONArray()

        assertThat((0 until json.length()).map { json.getJSONObject(it).getString("time") })
            .containsExactly("00:00", "01:00", "03:00").inOrder()
        assertThat((0 until json.length()).map { json.getJSONObject(it).getInt("timeAsSeconds") })
            .containsExactly(0, 3600, 10800).inOrder()
    }

    @Test
    fun `hours past nine keep two digits`() {
        val json = blocks(10 to 1.0, 14 to 2.0).toJSONArray()

        assertThat(json.getJSONObject(1).getString("time")).isEqualTo("10:00")
    }

    /**
     * `String.format("%02d")` renders digits with the *locale's* zero digit, so under a locale like
     * ar-EG it produces "٠١:٠٠" — which no reader parses back. The editor used to build times that
     * way. Rendering must not depend on the device locale at all.
     */
    @Test
    fun `times are ASCII regardless of the device locale`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        val json = blocks(1 to 1.0, 23 to 2.0).toJSONArray()

        assertThat(json.getJSONObject(1).getString("time")).isEqualTo("01:00")
    }

    @Test
    fun `a rendered schedule parses back to the same blocks`() {
        val cases = listOf(
            singleBlock(0.1),
            blocks(1 to 1.0, 23 to 2.0),
            blocks(1 to 1.0, 2 to 2.0, 21 to 3.0),
            List(24) { Block(T.hours(1).msecs(), it * 0.05) }
        )

        cases.forEach { original ->
            assertWithMessage("round trip of %s", original)
                .that(blockFromJsonArray(original.toJSONArray(), dateUtil)).isEqualTo(original)
        }
    }

    @Test
    fun `a rendered target schedule parses back to the same blocks`() {
        val cases = listOf(
            singleTargetBlock(110.0, 120.0),
            targets(Triple(6, 100.0, 110.0), Triple(18, 105.0, 115.0)),
            List(24) { TargetBlock(T.hours(1).msecs(), 100.0 + it, 120.0 + it) }
        )

        cases.forEach { original ->
            val parsed = targetBlockFromJsonArray(original.lowToJSONArray(), original.highToJSONArray(), dateUtil)
            assertWithMessage("round trip of %s", original).that(parsed).isEqualTo(original)
        }
    }

    /** Low and high are rendered as two arrays but must stay aligned entry for entry. */
    @Test
    fun `low and high render to matching times`() {
        val target = targets(Triple(6, 100.0, 110.0), Triple(18, 105.0, 115.0))

        val low = target.lowToJSONArray()
        val high = target.highToJSONArray()

        assertThat(low.length()).isEqualTo(high.length())
        for (i in 0 until low.length()) {
            assertThat(high.getJSONObject(i).getString("time")).isEqualTo(low.getJSONObject(i).getString("time"))
        }
        assertThat(low.getJSONObject(1).getDouble("value")).isEqualTo(105.0)
        assertThat(high.getJSONObject(1).getDouble("value")).isEqualTo(115.0)
    }

    /**
     * The shape a 3.4.x build wrote and still has to be able to read: exactly these three fields,
     * `time` as `HH:MM`. Adding or renaming one would be invisible to every test above, which only
     * check that our own reader agrees with our own writer.
     */
    @Test
    fun `an entry carries exactly time, timeAsSeconds and value`() {
        val entry = blocks(24 to 0.5).toJSONArray().getJSONObject(0)

        assertThat(entry.keys().asSequence().toList()).containsExactly("time", "timeAsSeconds", "value")
    }

    @Test
    fun `an empty schedule renders as an empty array rather than failing`() {
        assertThat(emptyList<Block>().toJSONArray().length()).isEqualTo(0)
        assertThat(emptyList<TargetBlock>().lowToJSONArray().length()).isEqualTo(0)
    }

    /**
     * A profile saved by an older build under a locale with non-ASCII digits.
     *
     * `String.format("%02d:00")` wrote the locale's own digits, and `DateUtil.toSeconds` matches
     * ASCII `\d` only, so it silently answers 0 for every entry. Nothing downstream notices: all-zero
     * start times still divide evenly by 3600, so the parse "succeeds" and returns blocks of zero
     * duration. Since the stored document is now re-rendered from these blocks, reading such a `time`
     * would flatten the whole schedule to 00:00 and publish that to the sync channel and to
     * Nightscout. Leading with `timeAsSeconds` makes the broken text irrelevant.
     */
    @Test
    fun `a time written with non-ASCII digits is harmless because timeAsSeconds leads`() {
        val arabicDigits = JSONArray(
            """[{"time":"٠٠:٠٠","timeAsSeconds":0,"value":0.6},
                {"time":"٠٦:٠٠","timeAsSeconds":21600,"value":1.2},
                {"time":"٢٠:٠٠","timeAsSeconds":72000,"value":0.9}]"""
        )

        val parsed = blockFromJsonArray(arabicDigits, dateUtil)

        assertThat(parsed).isEqualTo(
            listOf(
                Block(T.hours(6).msecs(), 0.6),
                Block(T.hours(14).msecs(), 1.2),
                Block(T.hours(4).msecs(), 0.9)
            )
        )
    }

    /** The same recovery for the paired target arrays. */
    @Test
    fun `non-ASCII target times are harmless too`() {
        val low = JSONArray("""[{"time":"٠٠:٠٠","timeAsSeconds":0,"value":100},{"time":"٠٦:٠٠","timeAsSeconds":21600,"value":105}]""")
        val high = JSONArray("""[{"time":"٠٠:٠٠","timeAsSeconds":0,"value":110},{"time":"٠٦:٠٠","timeAsSeconds":21600,"value":115}]""")

        assertThat(targetBlockFromJsonArray(low, high, dateUtil)).isEqualTo(
            listOf(TargetBlock(T.hours(6).msecs(), 100.0, 110.0), TargetBlock(T.hours(18).msecs(), 105.0, 115.0))
        )
    }

    /**
     * `timeAsSeconds` is the source of truth, so a non-zero one wins over a readable but disagreeing
     * `time`. This is the field the editor has always read its rows back from.
     */
    @Test
    fun `a non-zero timeAsSeconds wins over a disagreeing time`() {
        val conflicting = JSONArray(
            """[{"time":"00:00","timeAsSeconds":0,"value":1.0},
                {"time":"06:00","timeAsSeconds":28800,"value":2.0}]"""
        )

        // 28800 = 08:00, not the 06:00 the text claims.
        assertThat(blockFromJsonArray(conflicting, dateUtil))
            .isEqualTo(listOf(Block(T.hours(8).msecs(), 1.0), Block(T.hours(16).msecs(), 2.0)))
    }

    /**
     * The mirror of the ar-SA bug, and the reason zero means "ask `time`".
     *
     * An uploader that omits `timeAsSeconds` from a defaults-filled struct writes 0 on every entry.
     * Trusting that blindly would flatten the whole schedule to midnight - exactly the failure the
     * fallback exists to prevent, just from the other direction.
     */
    @Test
    fun `an all-zero timeAsSeconds falls back to time rather than flattening`() {
        val zeroed = JSONArray(
            """[{"time":"00:00","timeAsSeconds":0,"value":0.6},
                {"time":"06:00","timeAsSeconds":0,"value":1.2},
                {"time":"20:00","timeAsSeconds":0,"value":0.9}]"""
        )

        assertThat(blockFromJsonArray(zeroed, dateUtil)).isEqualTo(
            listOf(
                Block(T.hours(6).msecs(), 0.6),
                Block(T.hours(14).msecs(), 1.2),
                Block(T.hours(4).msecs(), 0.9)
            )
        )
    }

    /** A genuine midnight block is zero in both fields and must survive the fallback intact. */
    @Test
    fun `a real midnight block still reads as zero`() {
        val parsed = blockFromJsonArray(JSONArray("""[{"time":"00:00","timeAsSeconds":0,"value":0.7}]"""), dateUtil)

        assertThat(parsed).isEqualTo(listOf(Block(T.hours(24).msecs(), 0.7)))
    }

    /** Zero seconds with an unreadable time is still a midnight block, not a rejection. */
    @Test
    fun `zero seconds with an unreadable time is honoured`() {
        val parsed = blockFromJsonArray(JSONArray("""[{"time":"٠٠:٠٠","timeAsSeconds":0,"value":0.7}]"""), dateUtil)

        assertThat(parsed).isEqualTo(listOf(Block(T.hours(24).msecs(), 0.7)))
    }

    /** With neither field usable there is nothing to recover, so the profile is still invalid. */
    @Test
    fun `an unreadable time and no timeAsSeconds is still rejected`() {
        assertThat(blockFromJsonArray(JSONArray("""[{"time":"٠٦:٠٠","value":1.0}]"""), dateUtil)).isNull()
        assertThat(blockFromJsonArray(JSONArray("""[{"value":1.0}]"""), dateUtil)).isNull()
    }

}
