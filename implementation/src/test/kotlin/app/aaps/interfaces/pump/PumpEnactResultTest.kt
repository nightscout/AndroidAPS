package app.aaps.interfaces.pump

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.main.pump.toHtml
import app.aaps.plugins.aps.loop.extensions.json
import app.aaps.pump.virtual.extensions.toText
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class PumpEnactResultTest : TestBaseWithProfile() {

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    @BeforeEach
    fun mock() {
        `when`(rh.gs(app.aaps.core.ui.R.string.success)).thenReturn("Success")
        `when`(rh.gs(app.aaps.core.ui.R.string.enacted)).thenReturn("Enacted")
        `when`(rh.gs(app.aaps.core.ui.R.string.comment)).thenReturn("Comment")
        `when`(rh.gs(app.aaps.core.ui.R.string.configbuilder_insulin)).thenReturn("Insulin")
        `when`(rh.gs(app.aaps.core.ui.R.string.smb_shortname)).thenReturn("SMB")
        `when`(rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)).thenReturn("U")
        `when`(rh.gs(app.aaps.core.ui.R.string.cancel_temp)).thenReturn("Cancel temp basal")
        `when`(rh.gs(app.aaps.core.ui.R.string.duration)).thenReturn("Duration")
        `when`(rh.gs(app.aaps.core.ui.R.string.percent)).thenReturn("Percent")
        `when`(rh.gs(app.aaps.core.ui.R.string.absolute)).thenReturn("Absolute")
    }

    @Test fun successTest() {
        val per = PumpEnactResult(injector)

        per.success(true)
        assertThat(per.success).isTrue()
    }

    @Test fun enactedTest() {
        val per = PumpEnactResult(injector)

        per.enacted(true)
        assertThat(per.enacted).isTrue()
    }

    @Test fun commentTest() {
        val per = PumpEnactResult(injector)

        per.comment("SomeComment")
        assertThat(per.comment).isEqualTo("SomeComment")
    }

    @Test fun durationTest() {
        val per = PumpEnactResult(injector)

        per.duration(10)
        assertThat(per.duration.toLong()).isEqualTo(10L)
    }

    @Test fun absoluteTest() {
        val per = PumpEnactResult(injector)

        per.absolute(11.0)
        assertThat(per.absolute).isWithin(0.01).of(11.0)
    }

    @Test fun percentTest() {
        val per = PumpEnactResult(injector)

        per.percent(10)
        assertThat(per.percent).isEqualTo(10)
    }

    @Test fun isPercentTest() {
        val per = PumpEnactResult(injector)

        per.isPercent(true)
        assertThat(per.isPercent).isTrue()
    }

    @Test fun isTempCancelTest() {
        val per = PumpEnactResult(injector)

        per.isTempCancel(true)
        assertThat(per.isTempCancel).isTrue()
    }

    @Test fun bolusDeliveredTest() {
        val per = PumpEnactResult(injector)

        per.bolusDelivered(11.0)
        assertThat(per.bolusDelivered).isWithin(0.01).of(11.0)
    }

    @Test fun queuedTest() {
        val per = PumpEnactResult(injector)

        per.queued(true)
        assertThat(per.queued).isTrue()
    }

    @Test fun toStringTest() {
        var per = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Insulin: 10.0 U
    """.trimIndent()
        )
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Cancel temp basal
    """.trimIndent()
        )
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 20 min
    Percent: 90%
    """.trimIndent()
        )
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 30 min
    Absolute: 1.0 U/h
    """.trimIndent()
        )
        per = PumpEnactResult(injector).enacted(false).comment("AAA")
        assertThat(per.toText(rh)).isEqualTo(
            """
    Success: false
    Comment: AAA
    """.trimIndent()
        )
    }

    @Test fun toHtmlTest() {

        var per: PumpEnactResult = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>SMB</b>: 10.0 U")
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br>Cancel temp basal")
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 20 min<br><b>Percent</b>: 90%")
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 30 min<br><b>Absolute</b>: 1.00 U/h")
        per = PumpEnactResult(injector).enacted(false).comment("AAA")
        assertThat(per.toHtml(rh, decimalFormatter)).isEqualTo("<b>Success</b>: false<br><b>Comment</b>: AAA")
    }

    @Test fun jsonTest() {
        var o: JSONObject?

        var per: PumpEnactResult = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"smb":10}""", o, false)
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"rate":0,"duration":0}""", o, false)
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"rate":0.9,"duration":20}""", o, false)
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("""{"rate":1,"duration":30}""", o, false)
    }
}
