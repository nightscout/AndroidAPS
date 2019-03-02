package info.nightscout.androidaps.plugins.general.careportal.Dialogs;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.SPMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

import static info.nightscout.androidaps.plugins.general.careportal.CareportalFragment.PROFILESWITCH;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SP.class, MainApp.class, ConfigBuilderPlugin.class, Context.class, NSUpload.class, TreatmentsPlugin.class, TreatmentService.class, DatabaseHelper.class})
public class NewNSTreatmentDialogTest {

    NewNSTreatmentDialog dialog;
    ProfileSwitch profileSwitchUpload = null;

    @Test
    public void createNSTreatmentTest() throws Exception {
        // Test profile creation
        doAnswer(invocation -> {
            ProfileSwitch ps = invocation.getArgument(0);
            profileSwitchUpload = ps;
            return null;
        }).when(NSUpload.class, "uploadProfileSwitch", ArgumentMatchers.any());
        PROFILESWITCH.executeProfileSwitch = true;
        dialog.setOptions(PROFILESWITCH, R.string.careportal_profileswitch);
        dialog.profileStore = AAPSMocker.getValidProfileStore();
        JSONObject data = new JSONObject();
        data.put("profile", AAPSMocker.TESTPROFILENAME);
        data.put("duration", 0);
        data.put("percentage", 110);
        data.put("timeshift", 0);
        dialog.createNSTreatment(data);

        // Profile should be sent to NS
        Assert.assertEquals(AAPSMocker.TESTPROFILENAME, profileSwitchUpload.profileName);
    }

    @Before
    public void prepareMock() throws Exception {
        AAPSMocker.mockMainApp();
        SPMocker.prepareMock();
        SP.putString("profile", AAPSMocker.getValidProfileStore().getData().toString());
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockStrings();
        PowerMockito.mockStatic(NSUpload.class);
        AAPSMocker.mockTreatmentService();
        AAPSMocker.mockBus();
        AAPSMocker.mockDatabaseHelper();

        NSProfilePlugin profilePlugin = NSProfilePlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActiveProfileInterface())
                .thenReturn(profilePlugin);

        dialog = new NewNSTreatmentDialog();
    }
}