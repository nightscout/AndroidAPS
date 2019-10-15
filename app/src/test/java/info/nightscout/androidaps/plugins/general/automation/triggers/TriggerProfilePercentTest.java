package info.nightscout.androidaps.plugins.general.automation.triggers;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.utils.DateUtil;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, L.class})
public class TriggerProfilePercentTest {

    private long now = 1514766900000L;

    @Test
    public void shouldRunTest() {

        TriggerProfilePercent t = new TriggerProfilePercent().setValue(101d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerProfilePercent().setValue(100d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerProfilePercent().setValue(100d).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerProfilePercent().setValue(90d).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerProfilePercent().setValue(100d).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerProfilePercent().setValue(101d).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());

        t = new TriggerProfilePercent().setValue(215d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerProfilePercent().setValue(110d).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerProfilePercent().setValue(90d).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun());

        // already run
        t = new TriggerProfilePercent().setValue(101d).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());
    }

    @Test
    public void copyConstructorTest() {
        TriggerProfilePercent t = new TriggerProfilePercent().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerProfilePercent t1 = (TriggerProfilePercent) t.duplicate();
        Assert.assertEquals(213d, t1.getValue(), 0.01d);
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerProfilePercent t = new TriggerProfilePercent().setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1L, t.getLastRun());
    }

    private String bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"percentage\":110},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerProfilePercent\"}";

    @Test
    public void toJSONTest() {
        TriggerProfilePercent t = new TriggerProfilePercent().setValue(110d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(bgJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerProfilePercent t = new TriggerProfilePercent().setValue(120d).comparator(Comparator.Compare.IS_EQUAL);

        TriggerProfilePercent t2 = (TriggerProfilePercent) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(120d, t2.getValue(), 0.01d);
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.icon_actions_profileswitch), new TriggerProfilePercent().icon());
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.profilepercentage, new TriggerProfilePercent().friendlyName()); // not mocked
    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockL();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);
    }

}
