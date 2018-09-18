package info.nightscout.androidaps.plugins.general.automation.actions;

import com.squareup.otto.Bus;

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
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.T;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, Bus.class, ProfileFunctions.class, DateUtil.class})
public class TriggerBgTest {

    @Test
    public void shouldRunTest() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateOneCurrentRecordBgData());

        TriggerBg t = new TriggerBg().units(Constants.MMOL).threshold(4.1d).comparator(Trigger.ISEQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(214).comparator(Trigger.ISEQUAL);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(214).comparator(Trigger.ISEQUALORGREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(214).comparator(Trigger.ISEQUALORLOWER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(215).comparator(Trigger.ISEQUAL);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(215).comparator(Trigger.ISEQUALORLOWER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(215).comparator(Trigger.ISEQUALORGREATER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(213).comparator(Trigger.ISEQUALORGREATER);
        Assert.assertTrue(t.shouldRun());
        t = new TriggerBg().units(Constants.MGDL).threshold(213).comparator(Trigger.ISEQUALORLOWER);
        Assert.assertFalse(t.shouldRun());

        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(new ArrayList<>());
        t = new TriggerBg().units(Constants.MGDL).threshold(213).comparator(Trigger.ISEQUALORLOWER);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerBg().comparator(Trigger.NOTAVAILABLE);
        Assert.assertTrue(t.shouldRun());
    }

    String bgJson = "{\"data\":\"{\\\"comparator\\\":0,\\\"threshold\\\":4.1,\\\"units\\\":\\\"mmol\\\"}\",\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.TriggerBg\"}";

    @Test
    public void toJSONTest() {
        TriggerBg t = new TriggerBg().units(Constants.MMOL).threshold(4.1d).comparator(Trigger.ISEQUAL);
        Assert.assertEquals(bgJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerBg t = new TriggerBg().units(Constants.MMOL).threshold(4.1d).comparator(Trigger.ISEQUAL);

        TriggerBg t2 = (TriggerBg) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Trigger.ISEQUAL, t2.comparator);
        Assert.assertEquals(4.1d, t2.threshold, 0.01d);
        Assert.assertEquals(Constants.MMOL, t2.units);
    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockBus();
        AAPSMocker.mockDatabaseHelper();
        AAPSMocker.mockProfileFunctions();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(1514766900000L + T.mins(1).msecs());

    }

    List<BgReading> generateOneCurrentRecordBgData() {
        List<BgReading> list = new ArrayList<>();
        try {
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":214,\"mills\":1514766900000,\"direction\":\"Flat\"}"))));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

}
