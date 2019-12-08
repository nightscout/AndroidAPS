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

import java.util.ArrayList;
import java.util.List;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, IobCobCalculatorPlugin.class})
public class TriggerBgTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {
        when(IobCobCalculatorPlugin.getPlugin().getBgReadings()).thenReturn(generateOneCurrentRecordBgData());

        TriggerBg t = new TriggerBg().setUnits(Constants.MMOL).setValue(4.1d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(214).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(214).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(214).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(215).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(215).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(215).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun());

        when(IobCobCalculatorPlugin.getPlugin().getBgReadings()).thenReturn(new ArrayList<>());
        t = new TriggerBg().setUnits(Constants.MGDL).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().comparator(Comparator.Compare.IS_NOT_AVAILABLE);
        Assert.assertTrue(t.shouldRun());

        t = new TriggerBg().setUnits(Constants.MGDL).setValue(214).comparator(Comparator.Compare.IS_EQUAL).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void copyConstructorTest() {
        TriggerBg t = new TriggerBg().setUnits(Constants.MGDL).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerBg t1 = (TriggerBg) t.duplicate();
        Assert.assertEquals(213d, t1.getValue(), 0.01d);
        Assert.assertEquals(Constants.MGDL, t1.getUnits());
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    @Test
    public void executeTest() {
        TriggerBg t = new TriggerBg().setUnits(Constants.MGDL).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        t.executed(1);
        Assert.assertEquals(1l, t.getLastRun());
    }

    String bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"bg\":4.1,\"units\":\"mmol\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBg\"}";

    @Test
    public void toJSONTest() {
        TriggerBg t = new TriggerBg().setUnits(Constants.MMOL).setValue(4.1d).comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(bgJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerBg t = new TriggerBg().setUnits(Constants.MMOL).setValue(4.1d).comparator(Comparator.Compare.IS_EQUAL);

        TriggerBg t2 = (TriggerBg) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals(4.1d, t2.getValue(), 0.01d);
        Assert.assertEquals(Constants.MMOL, t2.getUnits());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.icon_cp_bgcheck), new TriggerBg().icon());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockIobCobCalculatorPlugin();
        AAPSMocker.mockProfileFunctions();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

    }

    List<BgReading> generateOneCurrentRecordBgData() {
        List<BgReading> list = new ArrayList<>();
        try {
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":214,\"mills\":" + (now - 1) + ",\"direction\":\"Flat\"}"))));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

}
