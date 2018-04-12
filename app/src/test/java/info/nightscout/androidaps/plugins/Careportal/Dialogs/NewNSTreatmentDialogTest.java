package info.nightscout.androidaps.plugins.Careportal.Dialogs;

import android.content.Context;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.BundleMock;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.utils.SP;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, Context.class})
public class NewNSTreatmentDialogTest {

    NewNSTreatmentDialog dialog;

    @Test
    public void createNSTreatmentTest() throws JSONException {
        // Test profile creation
        dialog.setOptions(CareportalFragment.PROFILESWITCH, R.string.careportal_profileswitch);
        JSONObject data = new JSONObject();
        data.put("profile", AAPSMocker.getValidProfile().getData());
        data.put("duration", 0);
        data.put("percentage", 110);
        data.put("timeshift", 0);
        dialog.createNSTreatment(data);

        Bundle bundles = AAPSMocker.intentSent.getExtras();
        Assert.assertTrue(bundles.getString("profile").contains("00:00"));
    }

    @Test
    public void doProfileSwitch() {
    }

    @Test
    public void doProfileSwitch1() {
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        AAPSMocker.mockBundle();

        dialog = new NewNSTreatmentDialog();
    }
}