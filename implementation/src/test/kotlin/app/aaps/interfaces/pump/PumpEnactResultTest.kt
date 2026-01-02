package app.aaps.interfaces.pump

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.plugins.aps.extensions.toHtml
import app.aaps.plugins.aps.loop.extensions.json
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
        whenever(rh.gs(app.aaps.core.ui.R.string.success)).thenReturn("Success")
        whenever(rh.gs(app.aaps.core.ui.R.string.enacted)).thenReturn("Enacted")
        whenever(rh.gs(app.aaps.core.ui.R.string.comment)).thenReturn("Comment")
        whenever(rh.gs(app.aaps.core.ui.R.string.configbuilder_insulin)).thenReturn("Insulin")
        whenever(rh.gs(app.aaps.core.ui.R.string.smb_shortname)).thenReturn("SMB")
        whenever(rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)).thenReturn("U")
        whenever(rh.gs(app.aaps.core.ui.R.string.cancel_temp)).thenReturn("Cancel temp basal")
        whenever(rh.gs(app.aaps.core.ui.R.string.duration)).thenReturn("Duration")
        whenever(rh.gs(app.aaps.core.ui.R.string.percent)).thenReturn("Percent")
        whenever(rh.gs(app.aaps.core.ui.R.string.absolute)).thenReturn("Absolute")
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

    @Test fun toHtmlTest() {

        var per: PumpEnactResult = PumpEnactResultObject(rh).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>SMB</b>: 10.0 U")
        per = PumpEnactResultObject(rh).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br>Cancel temp basal")
        per = PumpEnactResultObject(rh).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 20 min<br><b>Percent</b>: 90%")
        per = PumpEnactResultObject(rh).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 30 min<br><b>Absolute</b>: 1.00 U/h")
        per = PumpEnactResultObject(rh).enacted(false).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Comment</b>: AAA")
    }

    @Test fun jsonTest() {
        var o: JSONObject?

        var per: PumpEnactResult = PumpEnactResultObject(rh).enacted(true).bolusDelivered(10.0).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"smb":10}""", o, false)
        per = PumpEnactResultObject(rh).enacted(true).isTempCancel(true).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"rate":0,"duration":0}""", o, false)
        per = PumpEnactResultObject(rh).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"rate":0.9,"duration":20}""", o, false)
        per = PumpEnactResultObject(rh).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"rate":1,"duration":30}""", o, false)
    }
}
