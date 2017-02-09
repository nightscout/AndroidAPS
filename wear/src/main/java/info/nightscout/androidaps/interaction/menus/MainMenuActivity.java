package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;

import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.AAPSPreferences;
import info.nightscout.androidaps.interaction.actions.BolusActivity;
import info.nightscout.androidaps.interaction.actions.TempTargetActivity;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;
import info.nightscout.androidaps.interaction.actions.WizardActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class MainMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                "TTarget",
                "Bolus",
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
                intent = new Intent(this, TempTargetActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 1:
                intent = new Intent(this, BolusActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 2:
                intent = new Intent(this, WizardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 3:
                intent = new Intent(this, AAPSPreferences.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 4:
                ListenerService.requestData(this);
                break;
            case 5:
                intent = new Intent(this, StatusMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 6:
                intent = new Intent(this, FillMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
        }

    }
}
