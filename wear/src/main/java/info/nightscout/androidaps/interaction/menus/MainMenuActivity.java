package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import java.util.Vector;

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
        menuitems.add("TempT");
        if(showWizard) menuitems.add("Wizard");
        menuitems.add("eCarb");
        menuitems.add("Bolus");
        menuitems.add("Settings");
        menuitems.add("Status");
        if (showPrimeFill) menuitems.add("Prime/Fill");

        return menuitems.toArray(new String[menuitems.size()]);
    }

    @Override
    protected void doAction(String action) {

        Intent intent;

        if ("Settings".equals(action)) {
            intent = new Intent(this, AAPSPreferences.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("Re-Sync".equals(action)) {
            ListenerService.requestData(this);
        } else if ("TempT".equals(action)) {
            intent = new Intent(this, TempTargetActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("Bolus".equals(action)) {
            intent = new Intent(this, BolusActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("Wizard".equals(action)) {
            intent = new Intent(this, WizardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("Status".equals(action)) {
            intent = new Intent(this, StatusMenuActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("Prime/Fill".equals(action)) {
            intent = new Intent(this, FillMenuActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        } else if ("eCarb".equals(action)) {
        intent = new Intent(this, ECarbActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(intent);
    }
    }
}
