package app.aaps.interfaces.pump

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.plugins.aps.loop.extensions.jsonObject
import app.aaps.pump.virtual.extensions.toText
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class PumpEnactResultTest : TestBaseWithProfile() {

    @BeforeEach
    fun mock() {
        whenever(rh.gs(CoreUiStrings.success)).thenReturn("Success")
        whenever(rh.gs(CoreUiStrings.enacted)).thenReturn("Enacted")
        whenever(rh.gs(CoreUiStrings.comment)).thenReturn("Comment")
        whenever(rh.gs(CoreUiStrings.configbuilder_insulin)).thenReturn("Insulin")
        whenever(rh.gs(CoreUiStrings.smb_shortname)).thenReturn("SMB")
        whenever(rh.gs(CoreUiStrings.insulin_unit_shortname)).thenReturn("U")
        whenever(rh.gs(CoreUiStrings.cancel_temp)).thenReturn("Cancel temp basal")
        whenever(rh.gs(CoreUiStrings.duration)).thenReturn("Duration")
        whenever(rh.gs(CoreUiStrings.percent)).thenReturn("Percent")
        whenever(rh.gs(CoreUiStrings.absolute)).thenReturn("Absolute")
    }

    @Test fun successTest() {
        val per = PumpEnactResultObject(rh)

        per.success(true)
        assertThat(per.success).isTrue()
    }

    @Test fun enactedTest() {
        val per = PumpEnactResultObject(rh)

        per.enacted(true)
        assertThat(per.enacted).isTrue()
    }

    @Test fun commentTest() {
        val per = PumpEnactResultObject(rh)

        per.comment("SomeComment")
        assertThat(per.comment).isEqualTo("SomeComment")
    }

    @Test fun durationTest() {
        val per = PumpEnactResultObject(rh)

        per.duration(10)
        assertThat(per.duration.toLong()).isEqualTo(10L)
    }

    @Test fun absoluteTest() {
        val per = PumpEnactResultObject(rh)

        per.absolute(11.0)
        assertThat(per.absolute).isWithin(0.01).of(11.0)
    }

    @Test fun percentTest() {
        val per = PumpEnactResultObject(rh)

        per.percent(10)
        assertThat(per.percent).isEqualTo(10)
    }

    @Test fun isPercentTest() {
        val per = PumpEnactResultObject(rh)

        per.isPercent(true)
        assertThat(per.isPercent).isTrue()
    }

    @Test fun isTempCancelTest() {
        val per = PumpEnactResultObject(rh)

        per.isTempCancel(true)
        assertThat(per.isTempCancel).isTrue()
    }

    @Test fun bolusDeliveredTest() {
        val per = PumpEnactResultObject(rh)

        per.bolusDelivered(11.0)
        assertThat(per.bolusDelivered).isWithin(0.01).of(11.0)
    }

    @Test fun queuedTest() {
        val per = PumpEnactResultObject(rh)

        per.queued(true)
        assertThat(per.queued).isTrue()
    }

    @Test fun toStringTest() {
        var per = PumpEnactResultObject(rh).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Insulin: 10.0 U
    """.trimIndent()
        )
        per = PumpEnactResultObject(rh).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Cancel temp basal
    """.trimIndent()
        )
        per = PumpEnactResultObject(rh).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 20 min
    Percent: 90%
    """.trimIndent()
        )
        per = PumpEnactResultObject(rh).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 30 min
    Absolute: 1.0 U/h
    """.trimIndent()
        )
        per = PumpEnactResultObject(rh).enacted(false).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
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
        var per: PumpEnactResult = PumpEnactResultObject(rh).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"smb":10.0}""")
        per = PumpEnactResultObject(rh).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"rate":0,"duration":0}""")
        per = PumpEnactResultObject(rh).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"rate":0.9,"duration":20}""")
        per = PumpEnactResultObject(rh).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.jsonObject(validProfile.getBasal()).toString()).isEqualTo("""{"rate":1.0,"duration":30}""")
    }
}
