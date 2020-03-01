package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import java.util.Vector;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.AAPSPreferences;
import info.nightscout.androidaps.interaction.actions.AcceptActivity;
import info.nightscout.androidaps.interaction.actions.BolusActivity;
import info.nightscout.androidaps.interaction.actions.ECarbActivity;
import info.nightscout.androidaps.interaction.actions.TempTargetActivity;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;
import info.nightscout.androidaps.interaction.actions.WizardActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class MainMenuActivity extends MenuListActivity {

    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        ListenerService.requestData(this);
    }

    @Override
    protected String[] getElements() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if(!sharedPreferences.getBoolean("wearcontrol", false)){
            return new String[] {
                    "Settings",
                    "Re-Sync"};
        }


        boolean showPrimeFill  = sp.getBoolean("primefill", false);
        boolean showWizard  = sp.getBoolean("showWizard", true);

        Vector<String> menuitems = new Vector<String>();
        menuitems.add(aaps.gs(R.string.menu_tempt));
        if(showWizard) menuitems.add(aaps.gs(R.string.menu_wizard));
        menuitems.add(aaps.gs(R.string.menu_ecarb));
        menuitems.add(aaps.gs(R.string.menu_bolus));
        menuitems.add(aaps.gs(R.string.menu_settings));
        menuitems.add(aaps.gs(R.string.menu_status));
        if (showPrimeFill) menuitems.add(aaps.gs(R.string.menu_prime_fill));

        return menuitems.toArray(new String[menuitems.size()]);
    }

    @Override
    protected void doAction(String action) {

        Intent intent;

        if (aaps.gs(R.string.menu_settings).equals(action)) {
            intent = new Intent(this, AAPSPreferences.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("Re-Sync".equals(action)) {
            ListenerService.requestData(this);
        } else if (aaps.gs(R.string.menu_tempt).equals(action)) {
            intent = new Intent(this, TempTargetActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if (aaps.gs(R.string.menu_bolus).equals(action)) {
            intent = new Intent(this, BolusActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if (aaps.gs(R.string.menu_wizard).equals(action)) {
            intent = new Intent(this, WizardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if (aaps.gs(R.string.menu_status).equals(action)) {
            intent = new Intent(this, StatusMenuActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if (aaps.gs(R.string.menu_prime_fill).equals(action)) {
            intent = new Intent(this, FillMenuActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if (aaps.gs(R.string.menu_ecarb).equals(action)) {
        intent = new Intent(this, ECarbActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(intent);
    }
    }
}
