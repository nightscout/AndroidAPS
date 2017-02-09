package info.nightscout.androidaps.actions;

import android.content.Intent;

import info.nightscout.androidaps.ListenerService;
import info.nightscout.androidaps.NWPreferences;
import info.nightscout.androidaps.actions.utils.MenuListActivity;
import info.nightscout.androidaps.actions.wizard.WizardActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class FillMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                "Preset 1",
                "Preset 2",
                "Preset 3"};
    }

    @Override
    protected void doAction(int position) {
        switch (position) {
            case 0:
                ListenerService.initiateAction(this, "fillpreset 1");
                break;
            case 1:
                ListenerService.initiateAction(this, "fillpreset 2");
                break;
            case 2:
                ListenerService.initiateAction(this, "fillpreset 3");
                break;
        }

    }
}
