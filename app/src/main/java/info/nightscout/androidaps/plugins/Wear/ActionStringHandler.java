package info.nightscout.androidaps.plugins.Wear;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Actions.dialogs.FillDialog;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventTempTargetRangeChange;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

/**
 * Created by adrian on 09/02/17.
 */

public class ActionStringHandler {

    public static final int TIMEOUT = 65 * 1000;

    private static long lastSentTimestamp = 0;
    private static String lastConfirmActionString = null;
    private static BolusWizard lastBolusWizard = null;

    private static HandlerThread handlerThread = new HandlerThread(FillDialog.class.getSimpleName());
    static {
        handlerThread.start();
    }

    public synchronized static void handleInitiate(String actionstring){

        if(!BuildConfig.WEAR_CONTROL) return;


        lastBolusWizard = null;

        String rTitle = "CONFIRM"; //TODO: i18n
        String rMessage = "";
        String rAction = "";


        // do the parsing and check constraints
        String[] act = actionstring.split("\\s+");

        if ("fillpreset".equals(act[0])) {
            ///////////////////////////////////// PRIME/FILL
            double amount = 0d;
            if ("1".equals(act[1])) {
                amount = SP.getDouble("fill_button1", 0.3);
            } else if ("2".equals(act[1])) {
                amount = SP.getDouble("fill_button2", 0d);
            } else if ("3".equals(act[1])) {
                amount = SP.getDouble("fill_button3", 0d);
            } else {
                return;
            }
            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(amount);
            rMessage += MainApp.instance().getString(R.string.primefill) + ": " + insulinAfterConstraints + "U";
            if (insulinAfterConstraints - amount != 0)
                rMessage += "\n" + MainApp.instance().getString(R.string.constraintapllied);

            rAction += "fill " + insulinAfterConstraints;

        } else if ("fill".equals(act[0])) {
            ////////////////////////////////////////////// PRIME/FILL
            double amount = SafeParse.stringToDouble(act[1]);

            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(amount);
            rMessage += MainApp.instance().getString(R.string.primefill) + ": " + insulinAfterConstraints + "U";
            if (insulinAfterConstraints - amount != 0)
                rMessage += "\n" + MainApp.instance().getString(R.string.constraintapllied);

            rAction += "fill " + insulinAfterConstraints;

        } else if ("bolus".equals(act[0])) {
            ////////////////////////////////////////////// BOLUS
            double insulin = SafeParse.stringToDouble(act[1]);
            int carbs = SafeParse.stringToInt(act[2]);
            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
            Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(carbs);
            rMessage += MainApp.instance().getString(R.string.bolus) + ": " + insulinAfterConstraints + "U\n";
            rMessage += MainApp.instance().getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

            if ((insulinAfterConstraints - insulin != 0) || (carbsAfterConstraints - carbs != 0)) {
                rMessage += "\n" + MainApp.instance().getString(R.string.constraintapllied);
            }
            rAction += "bolus " + insulinAfterConstraints + " " + carbsAfterConstraints;

        } else if ("temptarget".equals(act[0])) {
            ///////////////////////////////////////////////////////// TEMPTARGET
            boolean isMGDL = Boolean.parseBoolean(act[1]);

            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            TempTargetRangePlugin tempTargetRangePlugin = (TempTargetRangePlugin) MainApp.getSpecificPlugin(TempTargetRangePlugin.class);
            if (!(Config.APS && tempTargetRangePlugin != null && tempTargetRangePlugin.isEnabled(PluginBase.GENERAL))) {
                sendError("TempTargets not possible! Please check your configuration.");
                return;
            }
            if (profile == null) {
                sendError("No profile found!");
                return;
            }
            if(profile.getUnits().equals(Constants.MGDL) != isMGDL){
                sendError("Different units used on watch and phone!");
                return;
            }

            int duration = SafeParse.stringToInt(act[2]);
            if (duration == 0){
                rMessage += "Zero-Temp-Target - cancelling running Temp-Targets?";
                rAction = "temptarget true 0 0 0";
            } else {
                double low = SafeParse.stringToDouble(act[3]);
                double high = SafeParse.stringToDouble(act[4]);
                if(!isMGDL){
                    low *= Constants.MMOLL_TO_MGDL;
                    high *= Constants.MMOLL_TO_MGDL;
                }
                if (low < Constants.VERY_HARD_LIMIT_TEMP_MIN_BG[0] || low > Constants.VERY_HARD_LIMIT_TEMP_MIN_BG[1]) {
                    sendError("Min-BG out of range!");
                    return;
                }
                if (high < Constants.VERY_HARD_LIMIT_TEMP_MAX_BG[0] || high > Constants.VERY_HARD_LIMIT_TEMP_MAX_BG[1]) {
                    sendError("Max-BG out of range!");
                    return;
                }
                rMessage += "Temptarget:\nMin: " + act[3] + "\nMax: " + act[4] + "\nDuration: " +  act[2];
                rAction = actionstring;

            }

        } else if ("status".equals(act[0])) {
            ////////////////////////////////////////////// STATUS
            rTitle = "STATUS";
            rAction = "statusmessage";
            if("pump".equals(act[1])){
                rTitle += " PUMP";
                rMessage = getPumpStatus();
            } else if("loop".equals(act[1])){
                rTitle += " LOOP";
                rMessage = getLoopStatus();

            } else if("targets".equals(act[1])){
                rTitle += " TARGETS";
                rMessage = getTargetsStatus();
            }

        } else if ("wizard".equals(act[0])) {
            ////////////////////////////////////////////// WIZARD
            Integer carbsBeforeConstraints = SafeParse.stringToInt(act[1]);
            Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(carbsBeforeConstraints);

            if(carbsAfterConstraints - carbsBeforeConstraints !=0){
                sendError("Carb constraint violation!"); return;
            }

            boolean useBG = Boolean.parseBoolean(act[2]);
            boolean useBolusIOB = Boolean.parseBoolean(act[3]);
            boolean useBasalIOB = Boolean.parseBoolean(act[4]);

            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null) {
                sendError("No profile found!"); return;
            }

            BgReading bgReading = GlucoseStatus.actualBg();
            if(bgReading==null && useBG){
                sendError("No recent BG to base calculation on!"); return;
            }
            DecimalFormat format = new DecimalFormat("0.00");
            BolusWizard bolusWizard = new BolusWizard();
            bolusWizard.doCalc(profile.getDefaultProfile(), carbsAfterConstraints, 0d, useBG?bgReading.valueToUnits(profile.getUnits()):0d, 0d, useBolusIOB, useBasalIOB, false, false);

            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(bolusWizard.calculatedTotalInsulin);
            if(insulinAfterConstraints - bolusWizard.calculatedTotalInsulin !=0){
                sendError("Insulin contraint violation!" +
                        "\nCannot deliver " + format.format(bolusWizard.calculatedTotalInsulin)  +"!");
                return;
            }


            double insulin = bolusWizard.calculatedTotalInsulin;
            if(bolusWizard.calculatedTotalInsulin < 0) {
                bolusWizard.calculatedTotalInsulin = 0d;
            }

            if(bolusWizard.calculatedTotalInsulin <=0 && bolusWizard.carbs <=0){
                rAction = "info";
                rTitle = "INFO";
            } else {
                rAction = actionstring;
            }
            rMessage += "Carbs: " + bolusWizard.carbs + "g";
            rMessage += "\nBolus: " + format.format(bolusWizard.calculatedTotalInsulin) + "U";
            rMessage += "\n_____________";
            rMessage += "\nCalc (IC:" + DecimalFormatter.to1Decimal(bolusWizard.ic) + ", " + "ISF:" + DecimalFormatter.to1Decimal(bolusWizard.sens) +  "): ";
            rMessage += "\nFrom Carbs: " + format.format(bolusWizard.insulinFromCarbs) + "U";
            if(useBG)rMessage += "\nFrom BG: " + format.format(bolusWizard.insulinFromBG) + "U";
            if(useBolusIOB)rMessage += "\nBolus IOB: " + format.format(bolusWizard.insulingFromBolusIOB) + "U";
            if(useBasalIOB)rMessage += "\nBasal IOB: " + format.format(bolusWizard.insulingFromBasalsIOB) + "U";

            lastBolusWizard = bolusWizard;

        } else return;

        // send result
        WearFragment.getPlugin(MainApp.instance()).requestActionConfirmation(rTitle, rMessage, rAction);
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = rAction;
    }

    @NonNull
    private static String getPumpStatus() {
        return MainApp.getConfigBuilder().shortStatus(false);
    }

    @NonNull
    private static String getLoopStatus() {
        String ret = "";
        // decide if enabled/disabled closed/open; what Plugin as APS?
        final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
        if(activeloop != null && activeloop.isEnabled(activeloop.getType())) {
            if (MainApp.getConfigBuilder().isClosedModeEnabled()) {
                ret += "CLOSED LOOP\n";
            } else {
                ret += "OPEN LOOP\n";
            }
            final APSInterface aps = MainApp.getConfigBuilder().getActiveAPS();
            ret += "APS: " + ((aps==null)?"NO APS SELECTED!":((PluginBase) aps).getName());
            if(activeloop.lastRun != null){
                if(activeloop.lastRun.lastAPSRun != null)
                    ret += "\nLast Run: " + DateUtil.timeString(activeloop.lastRun.lastAPSRun);

                if(activeloop.lastRun.lastEnact != null)
                    ret += "\nLast Enact: " + DateUtil.timeString(activeloop.lastRun.lastEnact);

            }



        } else {
            ret += "LOOP DISABLED\n";
        }
        return ret;

    }

    @NonNull
    private static String getTargetsStatus() {
        String ret = "";
        if (!Config.APS){
            return "Targets only apply in APS mode!";
        }
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null){
            return "No profile set :(";
        }

        //Check for Temp-Target:
        TempTargetRangePlugin tempTargetRangePlugin = (TempTargetRangePlugin) MainApp.getSpecificPlugin(TempTargetRangePlugin.class);
        if (Config.APS && tempTargetRangePlugin != null && tempTargetRangePlugin.isEnabled(PluginBase.GENERAL)) {
            TempTarget tempTarget = tempTargetRangePlugin.getTempTargetInProgress(new Date().getTime());
            if (tempTarget != null) {
                ret += "Temp Target: " + NSProfile.toUnitsString(tempTarget.low, NSProfile.fromMgdlToUnits(tempTarget.low, profile.getUnits()), profile.getUnits()) + " - " + NSProfile.toUnitsString(tempTarget.high, NSProfile.fromMgdlToUnits(tempTarget.high, profile.getUnits()), profile.getUnits());
                ret += "\nuntil: " + DateUtil.timeString(tempTarget.getPlannedTimeEnd());
                ret += "\n\n";
            }
        }

        //Default Range/Target
        Double maxBgDefault = Constants.MAX_BG_DEFAULT_MGDL;
        Double minBgDefault = Constants.MIN_BG_DEFAULT_MGDL;
        Double targetBgDefault = Constants.TARGET_BG_DEFAULT_MGDL;
        if (!profile.getUnits().equals(Constants.MGDL)) {
            maxBgDefault = Constants.MAX_BG_DEFAULT_MMOL;
            minBgDefault = Constants.MIN_BG_DEFAULT_MMOL;
            targetBgDefault = Constants.TARGET_BG_DEFAULT_MMOL;
        }
        ret += "DEFAULT RANGE: ";
        ret += SP.getDouble("openapsma_min_bg", minBgDefault) + " - " + SP.getDouble("openapsma_max_bg", maxBgDefault);
        ret += " target: " + SP.getDouble("openapsma_target_bg", targetBgDefault);
        return ret;
    }


    public synchronized static void handleConfirmation(String actionString){

        if(!BuildConfig.WEAR_CONTROL) return;


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
        } else if ("temptarget".equals(act[0])) {
            int duration = SafeParse.stringToInt(act[2]);
            double low = SafeParse.stringToDouble(act[3]);
            double high = SafeParse.stringToDouble(act[4]);
            boolean isMGDL = Boolean.parseBoolean(act[1]);
            if(!isMGDL){
                low *= Constants.MMOLL_TO_MGDL;
                high *= Constants.MMOLL_TO_MGDL;
            }
            generateTempTarget(duration, low, high);
        } else if ("wizard".equals(act[0])){
            //use last calculation as confirmed string matches

            doBolus(lastBolusWizard.calculatedTotalInsulin, lastBolusWizard.carbs);
            lastBolusWizard = null;
        } else if ("bolus".equals(act[0])) {
            double insulin = SafeParse.stringToDouble(act[1]);
            int carbs = SafeParse.stringToInt(act[2]);
            doBolus(insulin, carbs);
        }
        lastBolusWizard = null;
    }

    private static void generateTempTarget(int duration, double low, double high) {
        TempTarget tempTarget = new TempTarget();
        tempTarget.timeStart = new Date();
        tempTarget.duration = duration;
        tempTarget.reason = "WearPlugin";
        if(tempTarget.duration != 0) {
            tempTarget.low = low;
            tempTarget.high = high;
        } else {
            tempTarget.low = 0;
            tempTarget.high = 0;
        }
        tempTarget.setTimeIndex(tempTarget.getTimeIndex());
        Dao<TempTarget, Long> dao = null;
        try {
            dao = MainApp.getDbHelper().getDaoTempTargets();
            dao.createIfNotExists(tempTarget);
            MainApp.bus().post(new EventTempTargetRangeChange());

            //TODO: Nightscout-Treatment for Temp-Target!
            //ConfigBuilderPlugin.uploadCareportalEntryToNS(data);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void doFillBolus(final Double amount) {
        //if(1==1)return;
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                PumpEnactResult result = MainApp.getConfigBuilder().deliverTreatment(MainApp.getConfigBuilder().getActiveInsulin(), amount, 0, null, false);
                if (!result.success) {
                    sendError(MainApp.sResources.getString(R.string.treatmentdeliveryerror)  +
                            "\n" +
                            result.comment);
                }
            }
        });
    }

    private static void doBolus(final Double amount, final Integer carbs) {
        //if(1==1)return;
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                PumpEnactResult result = MainApp.getConfigBuilder().deliverTreatment(MainApp.getConfigBuilder().getActiveInsulin(), amount, carbs, null, true);
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
        lastBolusWizard = null;
    }
}
