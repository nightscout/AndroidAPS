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
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, TreatmentsPlugin.class})
public class TriggerTempTargetTest {

    TreatmentsPlugin treatmentsPlugin;

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {
        when(treatmentsPlugin.getTempTargetFromHistory()).thenReturn(null);

        TriggerTempTarget t = new TriggerTempTarget().comparator(ComparatorExists.Compare.EXISTS);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerTempTarget().comparator(ComparatorExists.Compare.NOT_EXISTS);
        Assert.assertTrue(t.shouldRun());

        when(treatmentsPlugin.getTempTargetFromHistory()).thenReturn(new TempTarget());

        t = new TriggerTempTarget().comparator(ComparatorExists.Compare.NOT_EXISTS);
        Assert.assertFalse(t.shouldRun());
        t = new TriggerTempTarget().comparator(ComparatorExists.Compare.EXISTS);
        Assert.assertTrue(t.shouldRun());

        t = new TriggerTempTarget().comparator(ComparatorExists.Compare.EXISTS).lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

    }

    @Test
    public void copyConstructorTest() {
        TriggerTempTarget t = new TriggerTempTarget().comparator(ComparatorExists.Compare.NOT_EXISTS);
        TriggerTempTarget t1 = (TriggerTempTarget) t.duplicate();
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t.getComparator().getValue());
    }

    String ttJson = "{\"data\":{\"comparator\":\"EXISTS\",\"lastRun\":0},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTempTarget\"}";

    @Test
    public void toJSONTest() {
        TriggerTempTarget t = new TriggerTempTarget().comparator(ComparatorExists.Compare.EXISTS);
        Assert.assertEquals(ttJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerTempTarget t = new TriggerTempTarget().comparator(ComparatorExists.Compare.NOT_EXISTS);

        TriggerTempTarget t2 = (TriggerTempTarget) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t2.getComparator().getValue());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_keyboard_tab), new TriggerTempTarget().icon());
    }


    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        treatmentsPlugin = AAPSMocker.mockTreatmentPlugin();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);

    }
}
