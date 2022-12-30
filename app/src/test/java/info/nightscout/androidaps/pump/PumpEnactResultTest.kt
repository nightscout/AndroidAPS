package info.nightscout.androidaps.pump

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.core.pump.toHtml
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.plugins.aps.loop.extensions.json
import info.nightscout.pump.virtual.extensions.toText
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
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
        `when`(rh.gs(info.nightscout.core.ui.R.string.success)).thenReturn("Success")
        `when`(rh.gs(info.nightscout.core.ui.R.string.enacted)).thenReturn("Enacted")
        `when`(rh.gs(info.nightscout.core.ui.R.string.comment)).thenReturn("Comment")
        `when`(rh.gs(info.nightscout.core.ui.R.string.configbuilder_insulin)).thenReturn("Insulin")
        `when`(rh.gs(info.nightscout.core.ui.R.string.smb_shortname)).thenReturn("SMB")
        `when`(rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname)).thenReturn("U")
        `when`(rh.gs(info.nightscout.core.ui.R.string.cancel_temp)).thenReturn("Cancel temp basal")
        `when`(rh.gs(info.nightscout.core.ui.R.string.duration)).thenReturn("Duration")
        `when`(rh.gs(info.nightscout.core.ui.R.string.percent)).thenReturn("Percent")
        `when`(rh.gs(info.nightscout.core.ui.R.string.absolute)).thenReturn("Absolute")
    }

    @Test fun successTest() {
        val per = PumpEnactResult(injector)

        per.success(true)
        Assertions.assertEquals(true, per.success)
    }

    @Test fun enactedTest() {
        val per = PumpEnactResult(injector)

        per.enacted(true)
        Assertions.assertEquals(true, per.enacted)
    }

    @Test fun commentTest() {
        val per = PumpEnactResult(injector)

        per.comment("SomeComment")
        Assertions.assertEquals("SomeComment", per.comment)
    }

    @Test fun durationTest() {
        val per = PumpEnactResult(injector)

        per.duration(10)
        Assertions.assertEquals(10, per.duration.toLong())
    }

    @Test fun absoluteTest() {
        val per = PumpEnactResult(injector)

        per.absolute(11.0)
        Assertions.assertEquals(11.0, per.absolute, 0.01)
    }

    @Test fun percentTest() {
        val per = PumpEnactResult(injector)

        per.percent(10)
        Assertions.assertEquals(10, per.percent)
    }

    @Test fun isPercentTest() {
        val per = PumpEnactResult(injector)

        per.isPercent(true)
        Assertions.assertEquals(true, per.isPercent)
    }

    @Test fun isTempCancelTest() {
        val per = PumpEnactResult(injector)

        per.isTempCancel(true)
        Assertions.assertEquals(true, per.isTempCancel)
    }

    @Test fun bolusDeliveredTest() {
        val per = PumpEnactResult(injector)

        per.bolusDelivered(11.0)
        Assertions.assertEquals(11.0, per.bolusDelivered, 0.01)
    }

    @Test fun queuedTest() {
        val per = PumpEnactResult(injector)

        per.queued(true)
        Assertions.assertEquals(true, per.queued)
    }

    @Test fun toStringTest() {
        var per = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        Assertions.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Insulin: 10.0 U
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        Assertions.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Cancel temp basal
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        Assertions.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 20 min
    Percent: 90%
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        Assertions.assertEquals(
            """
    Success: false
    Enacted: true
    Comment: AAA
    Duration: 30 min
    Absolute: 1.0 U/h
    """.trimIndent(), per.toText(rh)
        )
        per = PumpEnactResult(injector).enacted(false).comment("AAA")
        Assertions.assertEquals(
            """
    Success: false
    Comment: AAA
    """.trimIndent(), per.toText(rh)
        )
    }

    @Test fun toHtmlTest() {

        var per: PumpEnactResult = PumpEnactResult(injector).enacted(true).bolusDelivered(10.0).comment("AAA")
        Assertions.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>SMB</b>: 10.0 U", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(true).isTempCancel(true).comment("AAA")
        Assertions.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br>Cancel temp basal", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(true).isPercent(true).percent(90).duration(20).comment("AAA")
        Assertions.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 20 min<br><b>Percent</b>: 90%", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(true).isPercent(false).absolute(1.0).duration(30).comment("AAA")
        Assertions.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 30 min<br><b>Absolute</b>: 1.00 U/h", per.toHtml(rh))
        per = PumpEnactResult(injector).enacted(false).comment("AAA")
        Assertions.assertEquals("<b>Success</b>: false<br><b>Comment</b>: AAA", per.toHtml(rh))
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