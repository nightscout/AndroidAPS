package info.nightscout.androidaps.data;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.SPMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SP.class, MainApp.class})
public class QuickWizardTest {

    String data1 = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
            "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}";
    String data2 = "{\"buttonText\":\"Lunch\",\"carbs\":18,\"validFrom\":36000,\"validTo\":39600," +
            "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":1,\"useBasalIOB\":2,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}";

    JSONArray array;
    QuickWizard qv = new QuickWizard();

    public QuickWizardTest() {
        try {
            array = new JSONArray("[" + data1 + "," + data2 + "]");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        SPMocker.prepareMock();
    }

    @Test
    public void setDataTest() {
        qv.setData(array);
        Assert.assertEquals(2, qv.size());
    }

    @Test
    public void saveTest() {
        qv.setData(array);
        qv.save();
        Assert.assertEquals("[{\"useBolusIOB\":0,\"buttonText\":\"Meal\",\"useTrend\":0,\"carbs\":36,\"useCOB\":0,\"useBasalIOB\":0,\"useTemptarget\":0,\"useBG\":0,\"validFrom\":0,\"useSuperBolus\":0,\"validTo\":18000},{\"useBolusIOB\":1,\"buttonText\":\"Lunch\",\"useTrend\":0,\"carbs\":18,\"useCOB\":0,\"useBasalIOB\":2,\"useTemptarget\":0,\"useBG\":0,\"validFrom\":36000,\"useSuperBolus\":0,\"validTo\":39600}]", SP.getString("QuickWizard", "d"));
    }

    @Test
    public void getTest() {
        qv.setData(array);
        Assert.assertEquals("Lunch", qv.get(1).buttonText());
    }

    @Test
    public void isActive() {
    }

    @Test
    public void getActive() {
    }

    @Test
    public void newEmptyItemTest() {
        Assert.assertNotNull(qv.newEmptyItem());
    }

    @Test
    public void addOrUpdate() {
    }

    @Test
    public void remove() {
    }
}