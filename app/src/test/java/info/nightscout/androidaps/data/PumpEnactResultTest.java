package info.nightscout.androidaps.data;

import android.text.Html;
import android.text.Spanned;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skyscreamer.jsonassert.JSONAssert;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;

import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by mike on 29.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, Html.class})
public class PumpEnactResultTest {

    PumpEnactResult per = new PumpEnactResult();

    @Test
    public void successTest() throws Exception {
        per.success(true);
        Assert.assertEquals(true, per.success);
    }

    @Test
    public void enactedTest() throws Exception {
        per.enacted(true);
        Assert.assertEquals(true, per.enacted);
    }

    @Test
    public void commentTest() throws Exception {
        per.comment("SomeComment");
        Assert.assertEquals("SomeComment", per.comment);
    }

    @Test
    public void durationTest() throws Exception {
        per.duration(10);
        Assert.assertEquals(10, per.duration);
    }

    @Test
    public void absoluteTest() throws Exception {
        per.absolute(11d);
        Assert.assertEquals(11d, per.absolute, 0.01d);
    }

    @Test
    public void percentTest() throws Exception {
        per.percent(10);
        Assert.assertEquals((int) 10, per.percent);
    }

    @Test
    public void isPercentTest() throws Exception {
        per.isPercent(true);
        Assert.assertEquals(true, per.isPercent);
    }

    @Test
    public void isTempCancelTest() throws Exception {
        per.isTempCancel(true);
        Assert.assertEquals(true, per.isTempCancel);
    }

    @Test
    public void bolusDeliveredTest() throws Exception {
        per.bolusDelivered(11d);
        Assert.assertEquals(11d, per.bolusDelivered, 0.01d);
    }

    @Test
    public void carbsDeliveredTest() throws Exception {
        per.carbsDelivered(11d);
        Assert.assertEquals(11d, per.carbsDelivered, 0.01d);
    }

    @Test
    public void queuedTest() throws Exception {
        per.queued(true);
        Assert.assertEquals(true, per.queued);
    }

    @Test
    public void logTest() throws Exception {
        per = new PumpEnactResult();
        Assert.assertEquals("Success: false Enacted: false Comment:  Duration: -1 Absolute: -1.0 Percent: -1 IsPercent: false IsTempCancel: false bolusDelivered: 0.0 carbsDelivered: 0.0 Queued: false", per.log());
    }

    @Test
    public void toStringTest() throws Exception {
        per = new PumpEnactResult().enacted(true).bolusDelivered(10).comment("AAA");
        Assert.assertEquals("Success: false\n" +
                "Enacted: true\n" +
                "Comment: AAA\n" +
                "SMB: 10.0 U", per.toString());

        per = new PumpEnactResult().enacted(true).isTempCancel(true).comment("AAA");
        Assert.assertEquals("Success: false\n" +
                "Enacted: true\n" +
                "Comment: AAA\n" +
                "Cancel temp basal", per.toString());

        per = new PumpEnactResult().enacted(true).isPercent(true).percent(90).duration(20).comment("AAA");
        Assert.assertEquals("Success: false\n" +
                "Enacted: true\n" +
                "Comment: AAA\n" +
                "Duration: 20 min\n" +
                "Percent: 90%", per.toString());

        per = new PumpEnactResult().enacted(true).isPercent(false).absolute(1).duration(30).comment("AAA");
        Assert.assertEquals("Success: false\n" +
                "Enacted: true\n" +
                "Comment: AAA\n" +
                "Duration: 30 min\n" +
                "Absolute: 1.0 U/h", per.toString());

        per = new PumpEnactResult().enacted(false).comment("AAA");
        Assert.assertEquals("Success: false\n" +
                "Comment: AAA", per.toString());
    }

    @Test
    public void toHtmlTest() throws Exception {
        per = new PumpEnactResult().enacted(true).bolusDelivered(10).comment("AAA");
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>SMB</b>: 10.0 U", per.toHtml());

        per = new PumpEnactResult().enacted(true).isTempCancel(true).comment("AAA");
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br>Cancel temp basal", per.toHtml());

        per = new PumpEnactResult().enacted(true).isPercent(true).percent(90).duration(20).comment("AAA");
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 20 min<br><b>Percent</b>: 90%", per.toHtml());

        per = new PumpEnactResult().enacted(true).isPercent(false).absolute(1).duration(30).comment("AAA");
        Assert.assertEquals("<b>Success</b>: false<br><b>Enacted</b>: true<br><b>Comment</b>: AAA<br><b>Duration</b>: 30 min<br><b>Absolute</b>: 1.00 U/h", per.toHtml());

        per = new PumpEnactResult().enacted(false).comment("AAA");
        Assert.assertEquals("<b>Success</b>: false<br><b>Comment</b>: AAA", per.toHtml());
    }

    @Test
    public void jsonTest() throws Exception {
        JSONObject o;
        per = new PumpEnactResult().enacted(true).bolusDelivered(10).comment("AAA");
        o = per.json(AAPSMocker.getValidProfile());
        JSONAssert.assertEquals("{\"smb\":10}", o, false);

        per = new PumpEnactResult().enacted(true).isTempCancel(true).comment("AAA");
        o = per.json(AAPSMocker.getValidProfile());
        JSONAssert.assertEquals("{\"rate\":0,\"duration\":0}", o, false);

        per = new PumpEnactResult().enacted(true).isPercent(true).percent(90).duration(20).comment("AAA");
        o = per.json(AAPSMocker.getValidProfile());
        JSONAssert.assertEquals("{\"rate\":0.9,\"duration\":20}", o, false);

        per = new PumpEnactResult().enacted(true).isPercent(false).absolute(1).duration(30).comment("AAA");
        o = per.json(AAPSMocker.getValidProfile());
        JSONAssert.assertEquals("{\"rate\":1,\"duration\":30}", o, false);
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
    }
}
