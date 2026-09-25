package app.aaps.interfaces.pump

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.plugins.aps.loop.extensions.jsonObject
import app.aaps.pump.virtual.extensions.toText
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

/**
 * Pilot for resolving real text in tests instead of stubbing it.
 *
 * Every string here used to be stubbed - ten `whenever(rh.gs(...))` lines returning text someone typed
 * out by hand. [generatedTextResolver] answers from the generated English maps instead, so the asserts
 * below check what a user actually sees rather than what the stubs claimed. The expected strings did
 * not have to change: all ten matched `strings.xml` already, which is the point - a stub that agrees
 * with reality proves nothing, and one that disagrees hides a bug.
 */
class PumpEnactResultTest : TestBaseWithProfile() {

    private val text = generatedTextResolver()

    @Test fun successTest() {
        val per = PumpEnactResultObject(text)

        per.success(true)
        assertThat(per.success).isTrue()
    }

    @Test fun enactedTest() {
        val per = PumpEnactResultObject(text)

        per.enacted(true)
        assertThat(per.enacted).isTrue()
    }

    @Test fun commentTest() {
        val per = PumpEnactResultObject(text)

        per.comment("SomeComment")
        assertThat(per.comment).isEqualTo("SomeComment")
    }

    @Test fun durationTest() {
        val per = PumpEnactResultObject(text)

        per.duration(10)
        assertThat(per.duration.toLong()).isEqualTo(10L)
    }

    @Test fun absoluteTest() {
        val per = PumpEnactResultObject(text)

        per.absolute(11.0)
        assertThat(per.absolute).isWithin(0.01).of(11.0)
    }

    @Test fun percentTest() {
        val per = PumpEnactResultObject(text)

        per.percent(10)
        assertThat(per.percent).isEqualTo(10)
    }

    @Test fun isPercentTest() {
        val per = PumpEnactResultObject(text)

        per.isPercent(true)
        assertThat(per.isPercent).isTrue()
    }

    @Test fun isTempCancelTest() {
        val per = PumpEnactResultObject(text)

        per.isTempCancel(true)
        assertThat(per.isTempCancel).isTrue()
    }

    @Test fun bolusDeliveredTest() {
        val per = PumpEnactResultObject(text)

        per.bolusDelivered(11.0)
        assertThat(per.bolusDelivered).isWithin(0.01).of(11.0)
    }

    @Test fun queuedTest() {
        val per = PumpEnactResultObject(text)

        per.queued(true)
        assertThat(per.queued).isTrue()
    }

    @Test fun toStringTest() {
        var per = PumpEnactResultObject(text).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.toText(text)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Insulin: 10.0 U
    """.trimIndent()
        )
        per = PumpEnactResultObject(text).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.toText(text)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Cancel temp basal
    """.trimIndent()
        )
        per = PumpEnactResultObject(text).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.toText(text)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 20 min
    Percent: 90%
    """.trimIndent()
        )
        per = PumpEnactResultObject(text).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.toText(text)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 30 min
    Absolute: 1.0 U/h
    """.trimIndent()
        )
        per = PumpEnactResultObject(text).enacted(false).comment("AAA")
        assertThat(per.toText(text)).isEqualTo(
            """
    Success: false
    Comment: AAA
    """.trimIndent()
        )
    }

    /**
     * The document is kotlinx now, so a whole number prints as `10.0` where `org.json` printed `10`.
     * Nothing of this document is uploaded - the caller reads two values out of it - so the change is
     * confined to these expectations. The cancel branch still reports integer `0`, and must.
     */
    @Test fun jsonTest() {
        var per: PumpEnactResult = PumpEnactResultObject(text).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"smb":10.0}""")
        per = PumpEnactResultObject(text).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"rate":0,"duration":0}""")
        per = PumpEnactResultObject(text).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"rate":0.9,"duration":20}""")
        per = PumpEnactResultObject(text).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"rate":1.0,"duration":30}""")
    }
}
