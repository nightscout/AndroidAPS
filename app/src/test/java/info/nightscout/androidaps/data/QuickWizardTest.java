package info.nightscout.androidaps.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.SPMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SP.class, MainApp.class, Profile.class})
public class QuickWizardTest {

    private String data1 = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
            "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}";
    private String data2 = "{\"buttonText\":\"Lunch\",\"carbs\":18,\"validFrom\":36000,\"validTo\":39600," +
            "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":1,\"useBasalIOB\":2,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}";

    private JSONArray array;

    public QuickWizardTest() {
        try {
            array = new JSONArray("[" + data1 + "," + data2 + "]");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Before
    public void mock() throws Exception {
        AAPSMocker.mockMainApp();
        SPMocker.prepareMock();
        PowerMockito.mockStatic(Profile.class);
        PowerMockito.when(Profile.class, "secondsFromMidnight").thenReturn(0);

    }

    @Test
    public void setDataTest() {
        QuickWizard.INSTANCE.setData(array);
        Assert.assertEquals(2, QuickWizard.INSTANCE.size());
    }

    @Test
    public void saveTest() {
        QuickWizard.INSTANCE.setData(array);
        QuickWizard.INSTANCE.save();
        Assert.assertEquals("[{\"useBolusIOB\":0,\"buttonText\":\"Meal\",\"useTrend\":0,\"carbs\":36,\"useCOB\":0,\"useBasalIOB\":0,\"useTemptarget\":0,\"useBG\":0,\"validFrom\":0,\"useSuperBolus\":0,\"validTo\":18000},{\"useBolusIOB\":1,\"buttonText\":\"Lunch\",\"useTrend\":0,\"carbs\":18,\"useCOB\":0,\"useBasalIOB\":2,\"useTemptarget\":0,\"useBG\":0,\"validFrom\":36000,\"useSuperBolus\":0,\"validTo\":39600}]", SP.getString("QuickWizard", "d"));
    }

    @Test
    public void getTest() {
        QuickWizard.INSTANCE.setData(array);
        Assert.assertEquals("Lunch", QuickWizard.INSTANCE.get(1).buttonText());
    }

    @Test
    public void getActive() {
        QuickWizard.INSTANCE.setData(array);
        QuickWizardEntry e = QuickWizard.INSTANCE.getActive();
        Assert.assertEquals(36d, e.carbs(), 0.01d);
        QuickWizard.INSTANCE.remove(0);
        QuickWizard.INSTANCE.remove(0);
        Assert.assertNull(QuickWizard.INSTANCE.getActive());
    }

    @Test
    public void newEmptyItemTest() {
        Assert.assertNotNull(QuickWizard.INSTANCE.newEmptyItem());
    }

    @Test
    public void addOrUpdate() {
        QuickWizard.INSTANCE.setData(array);
        Assert.assertEquals(2, QuickWizard.INSTANCE.size());
        QuickWizard.INSTANCE.addOrUpdate(QuickWizard.INSTANCE.newEmptyItem());
        Assert.assertEquals(3, QuickWizard.INSTANCE.size());
        QuickWizardEntry q = QuickWizard.INSTANCE.newEmptyItem();
        q.position = 0;
        QuickWizard.INSTANCE.addOrUpdate(q);
        Assert.assertEquals(3, QuickWizard.INSTANCE.size());
    }

    @Test
    public void remove() {
        QuickWizard.INSTANCE.setData(array);
        QuickWizard.INSTANCE.remove(0);
        Assert.assertEquals(1, QuickWizard.INSTANCE.size());
    }
}