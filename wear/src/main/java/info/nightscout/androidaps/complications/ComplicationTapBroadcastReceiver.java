package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.complications.ProviderUpdateRequester;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.StringRes;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.interaction.actions.BolusActivity;
import info.nightscout.androidaps.interaction.actions.ECarbActivity;
import info.nightscout.androidaps.interaction.actions.WizardActivity;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;
import info.nightscout.androidaps.interaction.menus.StatusMenuActivity;
import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.DisplayFormat;
import info.nightscout.androidaps.interaction.utils.WearUtil;

/*
 * Created by dlvoy on 2019-11-12
 */
public class ComplicationTapBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = ComplicationTapBroadcastReceiver.class.getSimpleName();

    private static final String EXTRA_PROVIDER_COMPONENT =
            "info.nightscout.androidaps.complications.action.PROVIDER_COMPONENT";
    private static final String EXTRA_COMPLICATION_ID =
            "info.nightscout.androidaps.complications.action.COMPLICATION_ID";
    private static final String EXTRA_COMPLICATION_ACTION =
            "info.nightscout.androidaps.complications.action.COMPLICATION_ACTION";
    private static final String EXTRA_COMPLICATION_SINCE =
            "info.nightscout.androidaps.complications.action.COMPLICATION_SINCE";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        ComponentName provider = extras.getParcelable(EXTRA_PROVIDER_COMPONENT);
        int complicationId = extras.getInt(EXTRA_COMPLICATION_ID);
        String complicationAction = extras.getString(EXTRA_COMPLICATION_ACTION, ComplicationAction.MENU.toString());

        ComplicationAction action = ComplicationAction.MENU;
        try {
            action = ComplicationAction.valueOf(ComplicationAction.class, complicationAction);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // but how?
            Log.e(TAG, "Cannot interpret complication action: "+complicationAction);
        }

        action = remapActionWithUserPreferences(action);

        // Request an update for the complication that has just been tapped.
        ProviderUpdateRequester requester = new ProviderUpdateRequester(context, provider);
        requester.requestUpdate(complicationId);

        Intent intentOpen = null;

        switch (action) {
            case NONE:
                // do nothing
                return;
            case WIZARD:
                intentOpen = new Intent(aaps.getAppContext(), WizardActivity.class);
                break;
            case BOLUS:
                intentOpen = new Intent(aaps.getAppContext(), BolusActivity.class);
                break;
            case ECARB:
                intentOpen = new Intent(aaps.getAppContext(), ECarbActivity.class);
                break;
            case STATUS:
                intentOpen = new Intent(aaps.getAppContext(), StatusMenuActivity.class);
                break;
            case WARNING_OLD:
            case WARNING_SYNC:
                long oneAndHalfMinuteAgo = WearUtil.timestamp() - (Constants.MINUTE_IN_MS+Constants.SECOND_IN_MS * 30);
                long since = extras.getLong(EXTRA_COMPLICATION_SINCE, oneAndHalfMinuteAgo);
                @StringRes int labelId = (action == ComplicationAction.WARNING_SYNC) ?
                        R.string.msg_warning_sync : R.string.msg_warning_old;
                String msg = String.format(aaps.gs(labelId), DisplayFormat.shortTimeSince(since));
                Toast.makeText(aaps.getAppContext(), msg, Toast.LENGTH_LONG).show();
                break;
            case MENU:
            default:
                intentOpen = new Intent(aaps.getAppContext(), MainMenuActivity.class);
        }

        if (intentOpen != null) {
            // Perform intent - open dialog
            intentOpen.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            aaps.getAppContext().startActivity(intentOpen);
        }
    }

    private ComplicationAction remapActionWithUserPreferences(ComplicationAction originalAction) {
        final String userPrefAction = aaps.getComplicationTapAction();
        switch (originalAction) {
            case WARNING_OLD:
            case WARNING_SYNC:
                // warnings cannot be reconfigured by user
                return originalAction;
            default:
                switch (userPrefAction) {
                    case "menu":
                        return ComplicationAction.MENU;
                    case "wizard":
                        return ComplicationAction.WIZARD;
                    case "bolus":
                        return ComplicationAction.BOLUS;
                    case "ecarb":
                        return ComplicationAction.ECARB;
                    case "status":
                        return ComplicationAction.STATUS;
                    case "none":
                        return ComplicationAction.NONE;
                    case "default":
                    default:
                        return originalAction;
                }
        }
    }

    /**
     * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
     * toggled and updated.
     */
    static PendingIntent getTapActionIntent(
            Context context, ComponentName provider, int complicationId, ComplicationAction action) {
        Intent intent = new Intent(context, ComplicationTapBroadcastReceiver.class);
        intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider);
        intent.putExtra(EXTRA_COMPLICATION_ID, complicationId);
        intent.putExtra(EXTRA_COMPLICATION_ACTION, action.toString());


        // Pass complicationId as the requestCode to ensure that different complications get
        // different intents.
        return PendingIntent.getBroadcast(
                context, complicationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
     * toggled and updated.
     */
    static PendingIntent getTapWarningSinceIntent(
            Context context, ComponentName provider, int complicationId, ComplicationAction action, long since) {
        Intent intent = new Intent(context, ComplicationTapBroadcastReceiver.class);
        intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider);
        intent.putExtra(EXTRA_COMPLICATION_ID, complicationId);
        intent.putExtra(EXTRA_COMPLICATION_ACTION, action.toString());
        intent.putExtra(EXTRA_COMPLICATION_SINCE, since);


        // Pass complicationId as the requestCode to ensure that different complications get
        // different intents.
        return PendingIntent.getBroadcast(
                context, complicationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
