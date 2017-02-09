package info.nightscout.androidaps.actions;

import android.content.Context;
import android.content.Intent;

import info.nightscout.androidaps.ListenerService;
import info.nightscout.androidaps.NWPreferences;
import info.nightscout.androidaps.actions.bolus.BolusActivity;
import info.nightscout.androidaps.actions.bolus.GridActivity;
import info.nightscout.androidaps.actions.wizard.WizardActivity;

/**
 * Created by adrian on 08/02/17.
 */

final class ActionsDefinitions {

    private static final String[] ACTION_NAMES = {
            "Temp Target",
            "Wizard",
            "Settings",
            "Resend Data",
            "Fillpreset 1",
            "Fillpreset 2",
            "Fillpreset 3",
            "009"};


    public static void doAction(int position, Context ctx) {

        Intent intent;
        switch (position) {
            case 0:
                break;
            case 1:
                intent = new Intent(ctx, WizardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                break;
            case 2:
                intent = new Intent(ctx, NWPreferences.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                break;
            case 3:
                ListenerService.requestData(ctx);
                break;
            case 4:
                ListenerService.initiateAction(ctx, "fillpreset 1");
                break;
            case 5:
                ListenerService.initiateAction(ctx, "fillpreset 2");
                break;
            case 6:
                ListenerService.initiateAction(ctx, "fillpreset 3");
                break;
            case 7:
                intent = new Intent(ctx, GridActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                break;
        }

    }


    public static String[] getActionNames() {
        //posibility for later i18n
        return ACTION_NAMES;
    }

}
