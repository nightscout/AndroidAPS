package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import info.nightscout.androidaps.BuildConfig;
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

    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        ListenerService.requestData(this);
    }

    @Override
    protected String[] getElements() {

        if(!BuildConfig.WEAR_CONTROL){
            return new String[] {
                    "Settings",
                    "Re-Sync"};
        }


        boolean showPrimeFill  = sp.getBoolean("primefill", false);
        return new String[] {
                "TempT",
                "Bolus",
                "Wizard",
                "Settings",
                "Re-Sync",
                "Status",
                showPrimeFill?"Prime/Fill":""};
    }

    @Override
    protected void doAction(int position) {

        Intent intent;

        if(!BuildConfig.WEAR_CONTROL) {
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
            return;
        }


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
                boolean showPrimeFill  = sp.getBoolean("primefill", false);
                if(showPrimeFill) {
                    intent = new Intent(this, FillMenuActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    this.startActivity(intent);
                }
                break;
        }

    }
}
