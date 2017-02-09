package info.nightscout.androidaps.actions;

import android.content.Intent;

import info.nightscout.androidaps.ListenerService;
import info.nightscout.androidaps.NWPreferences;
import info.nightscout.androidaps.actions.utils.MenuListActivity;
import info.nightscout.androidaps.actions.wizard.WizardActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class MainMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                "TTarget",
                "Wizard",
                "Settings",
                "Re-Sync",
                "Status",
                "Prime/Fill"};
    }

    @Override
    protected void doAction(int position) {

        Intent intent;
        switch (position) {
            case 0:
                break;
            case 1:
                intent = new Intent(this, WizardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 2:
                intent = new Intent(this, NWPreferences.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 3:
                ListenerService.requestData(this);
                break;
            case 4:
                intent = new Intent(this, StatusMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 5:
                intent = new Intent(this, FillMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
        }

    }
}
