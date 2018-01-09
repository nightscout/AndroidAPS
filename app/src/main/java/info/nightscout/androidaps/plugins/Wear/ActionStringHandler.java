package info.nightscout.androidaps.plugins.Wear;

import android.os.HandlerThread;
import android.support.annotation.NonNull;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Actions.dialogs.FillDialog;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.queue.Callback;
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

    public synchronized static void handleInitiate(String actionstring) {

        if (!SP.getBoolean("wearcontrol", false)) return;

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

            Profile profile = MainApp.getConfigBuilder().getProfile();
            if (profile == null) {
                sendError("No profile found!");
                return;
            }
            if (profile.getUnits().equals(Constants.MGDL) != isMGDL) {
                sendError("Different units used on watch and phone!");
                return;
            }

            int duration = SafeParse.stringToInt(act[2]);
            if (duration == 0) {
                rMessage += "Zero-Temp-Target - cancelling running Temp-Targets?";
                rAction = "temptarget true 0 0 0";
            } else {
                double low = SafeParse.stringToDouble(act[3]);
                double high = SafeParse.stringToDouble(act[4]);
                if (!isMGDL) {
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
                rMessage += "Temptarget:\nMin: " + act[3] + "\nMax: " + act[4] + "\nDuration: " + act[2];
                rAction = actionstring;

            }

        } else if ("status".equals(act[0])) {
            ////////////////////////////////////////////// STATUS
            rTitle = "STATUS";
            rAction = "statusmessage";
            if ("pump".equals(act[1])) {
                rTitle += " PUMP";
                rMessage = getPumpStatus();
            } else if ("loop".equals(act[1])) {
                rTitle += " LOOP";
                rMessage = "TARGETS:\n" + getTargetsStatus();
                rMessage += "\n\n" + getLoopStatus();
                rMessage += "\n\nOAPS RESULT:\n" + getOAPSResultStatus();
            }

        } else if ("wizard".equals(act[0])) {
            ////////////////////////////////////////////// WIZARD
            Integer carbsBeforeConstraints = SafeParse.stringToInt(act[1]);
            Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(carbsBeforeConstraints);

            if (carbsAfterConstraints - carbsBeforeConstraints != 0) {
                sendError("Carb constraint violation!");
                return;
            }

            boolean useBG = Boolean.parseBoolean(act[2]);
            boolean useBolusIOB = Boolean.parseBoolean(act[3]);
            boolean useBasalIOB = Boolean.parseBoolean(act[4]);
            int percentage = Integer.parseInt(act[5]);

            Profile profile = MainApp.getConfigBuilder().getProfile();
            if (profile == null) {
                sendError("No profile found!");
                return;
            }

            BgReading bgReading = DatabaseHelper.actualBg();
            if (bgReading == null && useBG) {
                sendError("No recent BG to base calculation on!");
                return;
            }

            DecimalFormat format = new DecimalFormat("0.00");
            BolusWizard bolusWizard = new BolusWizard();
            bolusWizard.doCalc(profile, null, carbsAfterConstraints, 0d, useBG ? bgReading.valueToUnits(profile.getUnits()) : 0d, 0d, percentage, useBolusIOB, useBasalIOB, false, false);

            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(bolusWizard.calculatedTotalInsulin);
            if (insulinAfterConstraints - bolusWizard.calculatedTotalInsulin != 0) {
                sendError("Insulin contraint violation!" +
                        "\nCannot deliver " + format.format(bolusWizard.calculatedTotalInsulin) + "!");
                return;
            }


            if (bolusWizard.calculatedTotalInsulin < 0) {
                bolusWizard.calculatedTotalInsulin = 0d;
            }

            if (bolusWizard.calculatedTotalInsulin <= 0 && bolusWizard.carbs <= 0) {
                rAction = "info";
                rTitle = "INFO";
            } else {
                rAction = actionstring;
            }
            rMessage += "Carbs: " + bolusWizard.carbs + "g";
            rMessage += "\nBolus: " + format.format(bolusWizard.calculatedTotalInsulin) + "U";
            rMessage += "\n_____________";
            rMessage += "\nCalc (IC:" + DecimalFormatter.to1Decimal(bolusWizard.ic) + ", " + "ISF:" + DecimalFormatter.to1Decimal(bolusWizard.sens) + "): ";
            rMessage += "\nFrom Carbs: " + format.format(bolusWizard.insulinFromCarbs) + "U";
            if (useBG) rMessage += "\nFrom BG: " + format.format(bolusWizard.insulinFromBG) + "U";
            if (useBolusIOB)
                rMessage += "\nBolus IOB: " + format.format(bolusWizard.insulingFromBolusIOB) + "U";
            if (useBasalIOB)
                rMessage += "\nBasal IOB: " + format.format(bolusWizard.insulingFromBasalsIOB) + "U";
            if (percentage != 100) {
                rMessage += "\nPercentage: " + format.format(bolusWizard.totalBeforePercentageAdjustment) + "U * " + percentage + "% -> ~" + format.format(bolusWizard.calculatedTotalInsulin) + "U";
            }

            lastBolusWizard = bolusWizard;

        } else if ("opencpp".equals(act[0])) {
            ProfileSwitch activeProfileSwitch = MainApp.getConfigBuilder().getProfileSwitchFromHistory(System.currentTimeMillis());
            if (activeProfileSwitch == null) {
                sendError("No active profile switch!");
                return;
            } else {
                // read CPP values
                rTitle = "opencpp";
                rMessage = "opencpp";
                rAction = "opencpp" + " " + activeProfileSwitch.percentage + " " + activeProfileSwitch.timeshift;
            }

        } else if ("cppset".equals(act[0])) {
            ProfileSwitch activeProfileSwitch = MainApp.getConfigBuilder().getProfileSwitchFromHistory(System.currentTimeMillis());
            if (activeProfileSwitch == null) {
                sendError("No active profile switch!");
                return;
            } else {
                // read CPP values
                rMessage = "CPP:" + "\n\n" +
                        "Timeshift: " + act[1] + "\n" +
                        "Percentage: " + act[2] + "%";
                rAction = actionstring;
            }

        } else if ("tddstats".equals(act[0])) {
            Object activePump = MainApp.getConfigBuilder().getActivePump();
            PumpInterface dana = MainApp.getSpecificPlugin(DanaRPlugin.class);
            PumpInterface danaRS = MainApp.getSpecificPlugin(DanaRSPlugin.class);
            PumpInterface danaV2 = MainApp.getSpecificPlugin(DanaRv2Plugin.class);
            PumpInterface danaKorean = MainApp.getSpecificPlugin(DanaRKoreanPlugin.class);


            if ((dana == null || dana != activePump) &&
                    (danaV2 == null || danaV2 != activePump) &&
                    (danaKorean == null || danaKorean != activePump) &&
                    (danaRS == null || danaRS != activePump)
                    ) {
                sendError("Pump does not support TDDs!");
                return;
            } else {
                // check if DB up to date
                List<DanaRHistoryRecord> dummies = new LinkedList<DanaRHistoryRecord>();
                List<DanaRHistoryRecord> historyList = getTDDList(dummies);

                if (isOldData(historyList)) {
                    rTitle = "TDD";
                    rAction = "statusmessage";
                    rMessage = "OLD DATA - ";

                    //if pump is not busy: try to fetch data
                    final PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
                    if (pump.isBusy()) {
                        rMessage += MainApp.instance().getString(R.string.pumpbusy);
                    } else {
                        rMessage += "trying to fetch data from pump.";

                        ConfigBuilderPlugin.getCommandQueue().loadHistory(RecordTypes.RECORD_TYPE_DAILY, new Callback() {
                            @Override
                            public void run() {
                                List<DanaRHistoryRecord> dummies = new LinkedList<DanaRHistoryRecord>();
                                List<DanaRHistoryRecord> historyList = getTDDList(dummies);
                                if (isOldData(historyList)) {
                                    sendStatusmessage("TDD", "TDD: Still old data! Cannot load from pump.");
                                } else {
                                    sendStatusmessage("TDD", generateTDDMessage(historyList, dummies));
                                }
                                    }
                                });
                    }
                } else {
                    // if up to date: prepare, send (check if CPP is activated -> add CPP stats)
                    rTitle = "TDD";
                    rAction = "statusmessage";
                    rMessage = generateTDDMessage(historyList, dummies);
                }
            }

        } else return;


        // send result
        WearPlugin.getPlugin().requestActionConfirmation(rTitle, rMessage, rAction);
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = rAction;
    }

    private static String generateTDDMessage(List<DanaRHistoryRecord> historyList, List<DanaRHistoryRecord> dummies) {

        ProfileInterface activeProfile = MainApp.getConfigBuilder().getActiveProfileInterface();

        if (activeProfile == null) {
            return "No profile loaded :(";
        }

        DateFormat df = new SimpleDateFormat("dd.MM.");
        String message = "";

        double refTDD = activeProfile.getProfile().getDefaultProfile().baseBasalSum() * 2;

        int i = 0;
        double sum = 0d;
        double weighted03 = 0d;
        double weighted05 = 0d;
        double weighted07 = 0d;

        Collections.reverse(historyList);
        for (DanaRHistoryRecord record : historyList) {
            double tdd = record.recordDailyBolus + record.recordDailyBasal;
            if (i == 0) {
                weighted03 = tdd;
                weighted05 = tdd;
                weighted07 = tdd;

            } else {
                weighted07 = (weighted07 * 0.3 + tdd * 0.7);
                weighted05 = (weighted05 * 0.5 + tdd * 0.5);
                weighted03 = (weighted03 * 0.7 + tdd * 0.3);
            }
            i++;
        }
        message += "weighted:\n";
        message += "0.3: " + DecimalFormatter.to2Decimal(weighted03) + "U " + (DecimalFormatter.to0Decimal(100 * weighted03 / refTDD) + "%") + "\n";
        message += "0.5: " + DecimalFormatter.to2Decimal(weighted05) + "U " + (DecimalFormatter.to0Decimal(100 * weighted05 / refTDD) + "%") + "\n";
        message += "0.7: " + DecimalFormatter.to2Decimal(weighted07) + "U " + (DecimalFormatter.to0Decimal(100 * weighted07 / refTDD) + "%") + "\n";
        message += "\n";

        PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
        if (pump != null && pump instanceof DanaRPlugin) {
            double tdd = DanaRPump.getInstance().dailyTotalUnits;
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n";
            message += "\n";
        }

        //add TDDs:
        Collections.reverse(historyList);
        for (DanaRHistoryRecord record : historyList) {
            double tdd = record.recordDailyBolus + record.recordDailyBasal;
            message += df.format(new Date(record.recordDate)) + " " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + (dummies.contains(record) ? "x" : "") + "\n";
        }
        return message;
    }

    public static boolean isOldData(List<DanaRHistoryRecord> historyList) {
        DateFormat df = new SimpleDateFormat("dd.MM.");
        return (historyList.size() < 3 || !(df.format(new Date(historyList.get(0).recordDate)).equals(df.format(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)))));
    }

    @NonNull
    public static List<DanaRHistoryRecord> getTDDList(List<DanaRHistoryRecord> returnDummies) {
        List<DanaRHistoryRecord> historyList = MainApp.getDbHelper().getDanaRHistoryRecordsByType(RecordTypes.RECORD_TYPE_DAILY);

        //only use newest 10
        historyList = historyList.subList(0, Math.min(10, historyList.size()));

        //fill single gaps
        List<DanaRHistoryRecord> dummies = (returnDummies != null) ? returnDummies : (new LinkedList());
        DateFormat df = new SimpleDateFormat("dd.MM.");
        for (int i = 0; i < historyList.size() - 1; i++) {
            DanaRHistoryRecord elem1 = historyList.get(i);
            DanaRHistoryRecord elem2 = historyList.get(i + 1);

            if (!df.format(new Date(elem1.recordDate)).equals(df.format(new Date(elem2.recordDate + 25 * 60 * 60 * 1000)))) {
                DanaRHistoryRecord dummy = new DanaRHistoryRecord();
                dummy.recordDate = elem1.recordDate - 24 * 60 * 60 * 1000;
                dummy.recordDailyBasal = elem1.recordDailyBasal / 2;
                dummy.recordDailyBolus = elem1.recordDailyBolus / 2;
                dummies.add(dummy);
                elem1.recordDailyBasal /= 2;
                elem1.recordDailyBolus /= 2;
            }
        }
        historyList.addAll(dummies);
        Collections.sort(historyList, new Comparator<DanaRHistoryRecord>() {
            @Override
            public int compare(DanaRHistoryRecord lhs, DanaRHistoryRecord rhs) {
                return (int) (rhs.recordDate - lhs.recordDate);
            }
        });
        return historyList;
    }

    @NonNull
    private static String getPumpStatus() {
        return ConfigBuilderPlugin.getActivePump().shortStatus(false);
    }

    @NonNull
    private static String getLoopStatus() {
        String ret = "";
        // decide if enabled/disabled closed/open; what Plugin as APS?
        final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
        if (activeloop != null && activeloop.isEnabled(activeloop.getType())) {
            if (MainApp.getConfigBuilder().isClosedModeEnabled()) {
                ret += "CLOSED LOOP\n";
            } else {
                ret += "OPEN LOOP\n";
            }
            final APSInterface aps = MainApp.getConfigBuilder().getActiveAPS();
            ret += "APS: " + ((aps == null) ? "NO APS SELECTED!" : ((PluginBase) aps).getName());
            if (activeloop.lastRun != null) {
                if (activeloop.lastRun.lastAPSRun != null)
                    ret += "\nLast Run: " + DateUtil.timeString(activeloop.lastRun.lastAPSRun);

                if (activeloop.lastRun.lastEnact != null)
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
        if (!Config.APS) {
            return "Targets only apply in APS mode!";
        }
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null) {
            return "No profile set :(";
        }

        //Check for Temp-Target:
        TempTarget tempTarget = MainApp.getConfigBuilder().getTempTargetFromHistory();
        if (tempTarget != null) {
            ret += "Temp Target: " + Profile.toTargetRangeString(tempTarget.low, tempTarget.low, Constants.MGDL, profile.getUnits());
            ret += "\nuntil: " + DateUtil.timeString(tempTarget.originalEnd());
            ret += "\n\n";
        }

        ret += "DEFAULT RANGE: ";
        ret += profile.getTargetLow() + " - " + profile.getTargetHigh();
        ret += " target: " + (profile.getTargetLow() + profile.getTargetHigh()) / 2;
        return ret;
    }

    private static String getOAPSResultStatus() {
        String ret = "";
        if (!Config.APS) {
            return "Only apply in APS mode!";
        }
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null) {
            return "No profile set :(";
        }

        APSInterface usedAPS = MainApp.getConfigBuilder().getActiveAPS();
        if (usedAPS == null) {
            return "No active APS :(!";
        }

        APSResult result = usedAPS.getLastAPSResult();
        if (result == null) {
            return "Last result not available!";
        }

        if (!result.changeRequested) {
            ret += MainApp.sResources.getString(R.string.nochangerequested) + "\n";
        } else if (result.rate == 0 && result.duration == 0) {
            ret += MainApp.sResources.getString(R.string.canceltemp) + "\n";
        } else {
            ret += MainApp.sResources.getString(R.string.rate) + ": " + DecimalFormatter.to2Decimal(result.rate) + " U/h " +
                    "(" + DecimalFormatter.to2Decimal(result.rate / ConfigBuilderPlugin.getActivePump().getBaseBasalRate() * 100) + "%)\n" +
                    MainApp.sResources.getString(R.string.duration) + ": " + DecimalFormatter.to0Decimal(result.duration) + " min\n";
        }
        ret += "\n" + MainApp.sResources.getString(R.string.reason) + ": " + result.reason;

        return ret;
    }


    public synchronized static void handleConfirmation(String actionString) {

        if (!SP.getBoolean("wearcontrol", false)) return;


        //Guard from old or duplicate confirmations
        if (lastConfirmActionString == null) return;
        if (!lastConfirmActionString.equals(actionString)) return;
        if (System.currentTimeMillis() - lastSentTimestamp > TIMEOUT) return;
        lastConfirmActionString = null;

        // do the parsing, check constraints and enact!
        String[] act = actionString.split("\\s+");

        if ("fill".equals(act[0])) {
            Double amount = SafeParse.stringToDouble(act[1]);
            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(amount);
            if (amount - insulinAfterConstraints != 0) {
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
            if (!isMGDL) {
                low *= Constants.MMOLL_TO_MGDL;
                high *= Constants.MMOLL_TO_MGDL;
            }
            generateTempTarget(duration, low, high);
        } else if ("wizard".equals(act[0])) {
            //use last calculation as confirmed string matches

            doBolus(lastBolusWizard.calculatedTotalInsulin, lastBolusWizard.carbs);
            lastBolusWizard = null;
        } else if ("bolus".equals(act[0])) {
            double insulin = SafeParse.stringToDouble(act[1]);
            int carbs = SafeParse.stringToInt(act[2]);
            doBolus(insulin, carbs);
        } else if ("cppset".equals(act[0])) {
            int timeshift = SafeParse.stringToInt(act[1]);
            int percentage = SafeParse.stringToInt(act[2]);
            setCPP(timeshift, percentage);
        } else if ("dismissoverviewnotification".equals(act[0])) {
            MainApp.bus().post(new EventDismissNotification(SafeParse.stringToInt(act[1])));
        }
        lastBolusWizard = null;
    }

    private static void setCPP(int timeshift, int percentage) {

        String msg = "";


        //check for validity
        if (percentage < Constants.CPP_MIN_PERCENTAGE || percentage > Constants.CPP_MAX_PERCENTAGE) {
            msg += String.format(MainApp.sResources.getString(R.string.openapsma_valueoutofrange), "Profile-Percentage") + "\n";
        }
        if (timeshift < 0 || timeshift > 23) {
            msg += String.format(MainApp.sResources.getString(R.string.openapsma_valueoutofrange), "Profile-Timeshift") + "\n";
        }
        final Profile profile = MainApp.getConfigBuilder().getProfile();

        if (profile == null || profile.getBasal() == null) {
            msg += MainApp.sResources.getString(R.string.cpp_notloadedplugins) + "\n";
        }
        if (!"".equals(msg)) {
            msg += MainApp.sResources.getString(R.string.cpp_valuesnotstored);
            String rTitle = "STATUS";
            String rAction = "statusmessage";
            WearPlugin.getPlugin().requestActionConfirmation(rTitle, msg, rAction);
            lastSentTimestamp = System.currentTimeMillis();
            lastConfirmActionString = rAction;
            return;
        }

        //send profile to pumpe
        new NewNSTreatmentDialog(); //init
        NewNSTreatmentDialog.doProfileSwitch(0, percentage, timeshift);
    }

    private static void generateTempTarget(int duration, double low, double high) {
        TempTarget tempTarget = new TempTarget();
        tempTarget.date = System.currentTimeMillis();
        tempTarget.durationInMinutes = duration;
        tempTarget.reason = "WearPlugin";
        tempTarget.source = Source.USER;
        if (tempTarget.durationInMinutes != 0) {
            tempTarget.low = low;
            tempTarget.high = high;
        } else {
            tempTarget.low = 0;
            tempTarget.high = 0;
        }
        MainApp.getDbHelper().createOrUpdate(tempTarget);

        //TODO: Nightscout-Treatment for Temp-Target!
        //ConfigBuilderPlugin.uploadCareportalEntryToNS(data);
    }

    private static void doFillBolus(final Double amount) {
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.insulin = amount;
        detailedBolusInfo.isValid = false;
        detailedBolusInfo.source = Source.USER;
        ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    sendError(MainApp.sResources.getString(R.string.treatmentdeliveryerror) +
                            "\n" +
                            result.comment);
                }
            }
        });
    }

    private static void doBolus(final Double amount, final Integer carbs) {
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.insulin = amount;
        detailedBolusInfo.carbs = carbs;
        detailedBolusInfo.source = Source.USER;
        ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    sendError(MainApp.sResources.getString(R.string.treatmentdeliveryerror) +
                            "\n" +
                            result.comment);
                }
            }
        });
    }

    private synchronized static void sendError(String errormessage) {
        WearPlugin.getPlugin().requestActionConfirmation("ERROR", errormessage, "error");
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = null;
        lastBolusWizard = null;
    }

    private synchronized static void sendStatusmessage(String title, String message) {
        WearPlugin.getPlugin().requestActionConfirmation(title, message, "statusmessage");
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = null;
        lastBolusWizard = null;
    }

    public synchronized static void expectNotificationAction(String message, int id) {
        String actionstring = "dismissoverviewnotification " + id;
        WearPlugin.getPlugin().requestActionConfirmation("DISMISS", message, actionstring);
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = actionstring;
        lastBolusWizard = null;
    }
}
