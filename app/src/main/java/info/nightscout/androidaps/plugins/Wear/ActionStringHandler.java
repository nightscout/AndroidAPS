package info.nightscout.androidaps.plugins.Wear;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.Actions.dialogs.FillDialog;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

/**
 * Created by adrian on 09/02/17.
 */

public class ActionStringHandler {

    public static final int TIMEOUT = 65 * 1000;

    private static long lastSentTimestamp = 0;
    private static String lastConfirmActionString = null;
    private static SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());


    private static HandlerThread handlerThread = new HandlerThread(FillDialog.class.getSimpleName());
    static {
        handlerThread.start();
    }

    public synchronized static void handleInitiate(String actionstring){

        String rTitle = "CONFIRM"; //TODO: i18n
        String rMessage = "";
        String rAction = "";


        // do the parsing and check constraints
        String[] act = actionstring.split("\\s+");

        if ("fillpreset".equals(act[0])) {
            double amount = 0d;
            if ("1".equals(act[1])) {
                amount = SafeParse.stringToDouble(DecimalFormatter.to2Decimal(SafeParse.stringToDouble(sp.getString("fill_button1", "0.3"))));
            } else if ("2".equals(act[1])) {
                amount = SafeParse.stringToDouble(DecimalFormatter.to2Decimal(SafeParse.stringToDouble(sp.getString("fill_button2", "0"))));
            } else if ("3".equals(act[1])) {
                amount = SafeParse.stringToDouble(DecimalFormatter.to2Decimal(SafeParse.stringToDouble(sp.getString("fill_button3", "0"))));
            } else {
                return;
            }
            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(amount);
            rMessage += MainApp.instance().getString(R.string.primefill) + ": " + insulinAfterConstraints + "U";
            if (insulinAfterConstraints - amount != 0)
                rMessage += "\n" + MainApp.instance().getString(R.string.constraintapllied);

            rAction += "fill " + insulinAfterConstraints;

        } else if(false){
            //... add more actions

        } else return;




        // send result
        WearFragment.getPlugin(MainApp.instance()).requestActionConfirmation(rTitle, rMessage, rAction);
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = rAction;
    }


    public synchronized static void handleConfirmation(String actionString){

        //Guard from old or duplicate confirmations
        if (lastConfirmActionString == null) return;
        if (!lastConfirmActionString.equals(actionString)) return;
        if (System.currentTimeMillis() - lastSentTimestamp > TIMEOUT) return;
        lastConfirmActionString = null;

        // do the parsing, check constraints and enact!
        String[] act = actionString.split("\\s+");

        if ("fill".equals(act[0])){
            Double amount = SafeParse.stringToDouble(act[1]);
            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(amount);
            if(amount - insulinAfterConstraints != 0){
                ToastUtils.showToastInUiThread(MainApp.instance(), "aborting: previously applied constraint changed");
                sendError("aborting: previously applied constraint changed");
                return;
            }
            doFillBolus(amount);
        }



    }

    private static void doFillBolus(final Double amount) {
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                PumpEnactResult result = MainApp.getConfigBuilder().deliverTreatment(amount, 0, null, false);
                if (!result.success) {
                    sendError(MainApp.sResources.getString(R.string.treatmentdeliveryerror)  +
                            "\n" +
                            result.comment);
                }
            }
        });
    }

    private synchronized static void sendError(String errormessage){
        WearFragment.getPlugin(MainApp.instance()).requestActionConfirmation("ERROR", errormessage, "error");
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = null;
    }


}
