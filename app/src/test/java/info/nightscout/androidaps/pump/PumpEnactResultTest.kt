package info.nightscout.androidaps.pump

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.core.main.R
import info.nightscout.core.pump.toHtml
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.plugins.aps.loop.extensions.json
import info.nightscout.plugins.extensions.toText
import info.nightscout.plugins.sync.nsShared.extensions.log
import org.json.JSONObject
import org.junit.Assert
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
        `when`(rh.gs(R.string.success)).thenReturn("Success")
        `when`(rh.gs(R.string.enacted)).thenReturn("Enacted")
        `when`(rh.gs(R.string.comment)).thenReturn("Comment")
        `when`(rh.gs(R.string.configbuilder_insulin)).thenReturn("Insulin")
        `when`(rh.gs(R.string.smb_shortname)).thenReturn("SMB")
        `when`(rh.gs(R.string.insulin_unit_shortname)).thenReturn("U")
        `when`(rh.gs(R.string.cancel_temp)).thenReturn("Cancel temp basal")
        `when`(rh.gs(R.string.duration)).thenReturn("Duration")
        `when`(rh.gs(R.string.percent)).thenReturn("Percent")
        `when`(rh.gs(R.string.absolute)).thenReturn("Absolute")
    }

    @Test fun successTest() {
        val per = PumpEnactResult(injector)

        per.success(true)
        Assert.assertEquals(true, per.success)
    }

    @Test fun enactedTest() {
        val per = PumpEnactResult(injector)

        per.enacted(true)
        Assert.assertEquals(true, per.enacted)
    }

    @Test fun commentTest() {
        val per = PumpEnactResult(injector)

        per.comment("SomeComment")
        Assert.assertEquals("SomeComment", per.comment)
    }

    @Test fun durationTest() {
        val per = PumpEnactResult(injector)

        per.duration(10)
        Assert.assertEquals(10, per.duration.toLong())
    }

    @Test fun absoluteTest() {
        val per = PumpEnactResult(injector)

        per.absolute(11.0)
        Assert.assertEquals(11.0, per.absolute, 0.01)
    }

    @Test fun percentTest() {
        val per = PumpEnactResult(injector)

        per.percent(10)
        Assert.assertEquals(10, per.percent)
    }

    @Test fun isPercentTest() {
        val per = PumpEnactResult(injector)

        per.isPercent(true)
        Assert.assertEquals(true, per.isPercent)
    }

    @Test fun isTempCancelTest() {
        val per = PumpEnactResult(injector)

        per.isTempCancel(true)
        Assert.assertEquals(true, per.isTempCancel)
    }

    @Test fun bolusDeliveredTest() {
        val per = PumpEnactResult(injector)

        per.bolusDelivered(11.0)
        Assert.assertEquals(11.0, per.bolusDelivered, 0.01)
    }

    @Test fun carbsDeliveredTest() {
        val per = PumpEnactResult(injector)

        per.carbsDelivered(11.0)
        Assert.assertEquals(11.0, per.carbsDelivered, 0.01)
    }

    @Test fun queuedTest() {
        val per = PumpEnactResult(injector)

        per.queued(true)
        Assert.assertEquals(true, per.queued)
    }

    @Test fun logTest() {
        val per = PumpEnactResult(injector)

        Assert.assertEquals(
            "Success: false Enacted: false Comment:  Duration: -1 Absolute: -1.0 Percent: -1 IsPercent: false IsTempCancel: false bolusDelivered: 0.0 carbsDelivered: 0.0 Queued: false",
            per.log()
        )
    }

    @Test fun toStringTest() {
        var per = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        Assert.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Insulin: 10.0 U
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        Assert.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Cancel temp basal
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        Assert.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 20 min
    Percent: 90%
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        Assert.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 30 min
    Absolute: 1.0 U/h
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(false).comment("AAA")
        Assert.assertEquals(
            """
    Success: false
    Comment: AAA
    """.trimIndent(), per.toText(rh)
        )
    }

    @Test fun toHtmlTest() {

        var per: PumpEnactResult = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>SMB</b>: 10.0 U", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br>Cancel temp basal", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 20 min<br><b>Percent</b>: 90%", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 30 min<br><b>Absolute</b>: 1.00 U/h", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(false).comment("AAA")
        Assert.assertEquals("<b>Success</b>: false<br><b>Comment</b>: AAA", per.toHtml(rh))
    }

    @Test fun jsonTest() {
        var o: JSONObject?

        var per: PumpEnactResult = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("{\"smb\":10}", o, false)
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("{\"rate\":0,\"duration\":0}", o, false)
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("{\"rate\":0.9,\"duration\":20}", o, false)
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        o = per.json(validProfile.getBasal())
        JSONAssert.assertEquals("{\"rate\":1,\"duration\":30}", o, false)
    }
}