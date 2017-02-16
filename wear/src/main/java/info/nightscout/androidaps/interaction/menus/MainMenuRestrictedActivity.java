package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.AAPSPreferences;
import info.nightscout.androidaps.interaction.actions.BolusActivity;
import info.nightscout.androidaps.interaction.actions.TempTargetActivity;
import info.nightscout.androidaps.interaction.actions.WizardActivity;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class MainMenuRestrictedActivity extends MenuListActivity {

    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        ListenerService.requestData(this);
    }

    @Override
    protected String[] getElements() {
        return new String[] {
                "Settings",
                "Re-Sync"};
    }

    @Override
    protected void doAction(int position) {

        Intent intent;
        switch (position) {
            case 0:
                intent = new Intent(this, AAPSPreferences.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
                break;
            case 1:
                ListenerService.requestData(this);
                break;
        }

    }
}
