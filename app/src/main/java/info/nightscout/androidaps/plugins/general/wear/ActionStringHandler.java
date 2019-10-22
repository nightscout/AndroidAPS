package info.nightscout.androidaps.plugins.general.wear;

import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

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
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.ToastUtils;

/**
 * Created by adrian on 09/02/17.
 */

public class ActionStringHandler {

    public static final int TIMEOUT = 65 * 1000;

    private static long lastSentTimestamp = 0;
    private static String lastConfirmActionString = null;
    private static BolusWizard lastBolusWizard = null;

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
            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();
            rMessage += MainApp.gs(R.string.primefill) + ": " + insulinAfterConstraints + "U";
            if (insulinAfterConstraints - amount != 0)
                rMessage += "\n" + MainApp.gs(R.string.constraintapllied);

            rAction += "fill " + insulinAfterConstraints;

        } else if ("fill".equals(act[0])) {
            ////////////////////////////////////////////// PRIME/FILL
            double amount = SafeParse.stringToDouble(act[1]);

            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();
            rMessage += MainApp.gs(R.string.primefill) + ": " + insulinAfterConstraints + "U";
            if (insulinAfterConstraints - amount != 0)
                rMessage += "\n" + MainApp.gs(R.string.constraintapllied);

            rAction += "fill " + insulinAfterConstraints;

        } else if ("bolus".equals(act[0])) {
            ////////////////////////////////////////////// BOLUS
            double insulin = SafeParse.stringToDouble(act[1]);
            int carbs = SafeParse.stringToInt(act[2]);
            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(insulin)).value();
            Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(carbs)).value();
            rMessage += MainApp.gs(R.string.bolus) + ": " + insulinAfterConstraints + "U\n";
            rMessage += MainApp.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g";

            if ((insulinAfterConstraints - insulin != 0) || (carbsAfterConstraints - carbs != 0)) {
                rMessage += "\n" + MainApp.gs(R.string.constraintapllied);
            }
            rAction += "bolus " + insulinAfterConstraints + " " + carbsAfterConstraints;

        } else if ("temptarget".equals(act[0])) {
            ///////////////////////////////////////////////////////// TEMPTARGET
            boolean isMGDL = Boolean.parseBoolean(act[1]);

            Profile profile = ProfileFunctions.getInstance().getProfile();
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
                if (low < HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0] || low > HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1]) {
                    sendError("Min-BG out of range!");
                    return;
                }
                if (high < HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0] || high > HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1]) {
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
            sendError("Update APP on Watch!");
            return;
        } else if ("wizard2".equals(act[0])) {
            ////////////////////////////////////////////// WIZARD
            Integer carbsBeforeConstraints = SafeParse.stringToInt(act[1]);
            Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(carbsBeforeConstraints)).value();

            if (carbsAfterConstraints - carbsBeforeConstraints != 0) {
                sendError("Carb constraint violation!");
                return;
            }

            boolean useBG = SP.getBoolean(R.string.key_wearwizard_bg, true);
            boolean useTT = SP.getBoolean(R.string.key_wearwizard_tt, false);
            boolean useBolusIOB = SP.getBoolean(R.string.key_wearwizard_bolusiob, true);
            boolean useBasalIOB = SP.getBoolean(R.string.key_wearwizard_basaliob, true);
            boolean useCOB = SP.getBoolean(R.string.key_wearwizard_cob, true);
            boolean useTrend = SP.getBoolean(R.string.key_wearwizard_trend, false);
            int percentage = Integer.parseInt(act[2]);

            Profile profile = ProfileFunctions.getInstance().getProfile();
            String profileName = ProfileFunctions.getInstance().getProfileName();
            if (profile == null) {
                sendError("No profile found!");
                return;
            }

            BgReading bgReading = DatabaseHelper.actualBg();
            if (bgReading == null && useBG) {
                sendError("No recent BG to base calculation on!");
                return;
            }

            CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "Wizard wear");
            if (useCOB && (cobInfo == null || cobInfo.displayCob == null)) {
                sendError("Unknown COB! BG reading missing or recent app restart?");
                return;
            }

            DecimalFormat format = new DecimalFormat("0.00");
            DecimalFormat formatInt = new DecimalFormat("0");
            BolusWizard bolusWizard = new BolusWizard(profile, profileName, TreatmentsPlugin.getPlugin().getTempTargetFromHistory(),
                    carbsAfterConstraints, cobInfo.displayCob, bgReading.valueToUnits(profile.getUnits()),
                    0d, percentage, useBG, useCOB, useBolusIOB, useBasalIOB, false, useTT, useTrend);

            if (Math.abs(bolusWizard.getInsulinAfterConstraints() - bolusWizard.getCalculatedTotalInsulin()) >= 0.01) {
                sendError("Insulin constraint violation!" +
                        "\nCannot deliver " + format.format(bolusWizard.getCalculatedTotalInsulin()) + "!");
                return;
            }

            if (bolusWizard.getCalculatedTotalInsulin() <= 0 && bolusWizard.getCarbs() <= 0) {
                rAction = "info";
                rTitle = "INFO";
            } else {
                rAction = actionstring;
            }
            rMessage += "Carbs: " + bolusWizard.getCarbs() + "g";
            rMessage += "\nBolus: " + format.format(bolusWizard.getCalculatedTotalInsulin()) + "U";
            rMessage += "\n_____________";
            rMessage += "\nCalc (IC:" + DecimalFormatter.to1Decimal(bolusWizard.getIc()) + ", " + "ISF:" + DecimalFormatter.to1Decimal(bolusWizard.getSens()) + "): ";
            rMessage += "\nFrom Carbs: " + format.format(bolusWizard.getInsulinFromCarbs()) + "U";
            if (useCOB)
                rMessage += "\nFrom" + formatInt.format(cobInfo.displayCob) + "g COB : " + format.format(bolusWizard.getInsulinFromCOB()) + "U";
            if (useBG)
                rMessage += "\nFrom BG: " + format.format(bolusWizard.getInsulinFromBG()) + "U";
            if (useBolusIOB)
                rMessage += "\nBolus IOB: " + format.format(bolusWizard.getInsulinFromBolusIOB()) + "U";
            if (useBasalIOB)
                rMessage += "\nBasal IOB: " + format.format(bolusWizard.getInsulinFromBasalsIOB()) + "U";
            if (useTrend)
                rMessage += "\nFrom 15' trend: " + format.format(bolusWizard.getInsulinFromTrend()) + "U";
            if (percentage != 100) {
                rMessage += "\nPercentage: " + format.format(bolusWizard.getTotalBeforePercentageAdjustment()) + "U * " + percentage + "% -> ~" + format.format(bolusWizard.getCalculatedTotalInsulin()) + "U";
            }

            lastBolusWizard = bolusWizard;

        } else if ("opencpp".equals(act[0])) {
            ProfileSwitch activeProfileSwitch = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(System.currentTimeMillis());
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
            ProfileSwitch activeProfileSwitch = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(System.currentTimeMillis());
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
            Object activePump = ConfigBuilderPlugin.getPlugin().getActivePump();
            if (activePump != null) {
                // check if DB up to date
                List<TDD> dummies = new LinkedList<TDD>();
                List<TDD> historyList = getTDDList(dummies);

                if (isOldData(historyList)) {
                    rTitle = "TDD";
                    rAction = "statusmessage";
                    rMessage = "OLD DATA - ";

                    //if pump is not busy: try to fetch data
                    final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
                    if (pump.isBusy()) {
                        rMessage += MainApp.gs(R.string.pumpbusy);
                    } else {
                        rMessage += "trying to fetch data from pump.";

                        ConfigBuilderPlugin.getPlugin().getCommandQueue().loadTDDs(new Callback() {
                            @Override
                            public void run() {
                                List<TDD> dummies = new LinkedList<TDD>();
                                List<TDD> historyList = getTDDList(dummies);
                                if (isOldData(historyList)) {
                                    sendStatusmessage("TDD", "TDD: Still old data! Cannot load from pump.\n" + generateTDDMessage(historyList, dummies));
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

        } else if ("ecarbs".equals(act[0])) {
            ////////////////////////////////////////////// ECARBS
            int carbs = SafeParse.stringToInt(act[1]);
            int starttime = SafeParse.stringToInt(act[2]);
            int duration = SafeParse.stringToInt(act[3]);
            long starttimestamp = System.currentTimeMillis() + starttime * 60 * 1000;
            Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(carbs)).value();
            rMessage += MainApp.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g";
            rMessage += "\n" + MainApp.gs(R.string.time) + ": " + DateUtil.timeString(starttimestamp);
            rMessage += "\n" + MainApp.gs(R.string.duration) + ": " + duration + "h";


            if ((carbsAfterConstraints - carbs != 0)) {
                rMessage += "\n" + MainApp.gs(R.string.constraintapllied);
            }
            if (carbsAfterConstraints <= 0) {
                sendError("Carbs = 0! No action taken!");
                return;
            }
            rAction += "ecarbs " + carbsAfterConstraints + " " + starttimestamp + " " + duration;

        } else if ("changeRequest".equals(act[0])) {
            ////////////////////////////////////////////// CHANGE REQUEST
            rTitle = MainApp.gs(R.string.openloop_newsuggestion);
            rAction = "changeRequest";
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            rMessage += finalLastRun.constraintsProcessed;

            WearPlugin.getPlugin().requestChangeConfirmation(rTitle, rMessage, rAction);
            lastSentTimestamp = System.currentTimeMillis();
            lastConfirmActionString = rAction;
            return;
        } else if ("cancelChangeRequest".equals(act[0])) {
            ////////////////////////////////////////////// CANCEL CHANGE REQUEST NOTIFICATION
            rAction = "cancelChangeRequest";

            WearPlugin.getPlugin().requestNotificationCancel(rAction);
            return;
        } else return;


        // send result
        WearPlugin.getPlugin().requestActionConfirmation(rTitle, rMessage, rAction);
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = rAction;
    }

    private static String generateTDDMessage(List<TDD> historyList, List<TDD> dummies) {

        Profile profile = ProfileFunctions.getInstance().getProfile();

        if (profile == null) {
            return "No profile loaded :(";
        }

        if (historyList.isEmpty()) {
            return "No history data!";
        }

        DateFormat df = new SimpleDateFormat("dd.MM.");
        String message = "";

        double refTDD = profile.baseBasalSum() * 2;

        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (df.format(new Date(historyList.get(0).date)).equals(df.format(new Date()))) {
            double tdd = historyList.get(0).getTotal();
            historyList.remove(0);
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n";
            message += "\n";
        } else if (pump != null && pump instanceof DanaRPlugin) {
            double tdd = DanaRPump.getInstance().dailyTotalUnits;
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n";
            message += "\n";
        }

        int i = 0;
        double sum = 0d;
        double weighted03 = 0d;
        double weighted05 = 0d;
        double weighted07 = 0d;

        Collections.reverse(historyList);
        for (TDD record : historyList) {
            double tdd = record.getTotal();
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
        Collections.reverse(historyList);

        //add TDDs:
        for (TDD record : historyList) {
            double tdd = record.getTotal();
            message += df.format(new Date(record.date)) + " " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + (dummies.contains(record) ? "x" : "") + "\n";
        }
        return message;
    }

    public static boolean isOldData(List<TDD> historyList) {
        Object activePump = ConfigBuilderPlugin.getPlugin().getActivePump();
        PumpInterface dana = DanaRPlugin.getPlugin();
        PumpInterface danaRS = DanaRSPlugin.getPlugin();
        PumpInterface danaV2 = DanaRv2Plugin.getPlugin();
        PumpInterface danaKorean = DanaRKoreanPlugin.getPlugin();
        PumpInterface insight = LocalInsightPlugin.getPlugin();

        boolean startsYesterday = activePump == dana || activePump == danaRS || activePump == danaV2 || activePump == danaKorean || activePump == insight;

        DateFormat df = new SimpleDateFormat("dd.MM.");
        return (historyList.size() < 3 || !(df.format(new Date(historyList.get(0).date)).equals(df.format(new Date(System.currentTimeMillis() - (startsYesterday ? 1000 * 60 * 60 * 24 : 0))))));
    }

    @NonNull
    public static List<TDD> getTDDList(List<TDD> returnDummies) {
        List<TDD> historyList = MainApp.getDbHelper().getTDDs();

        historyList = historyList.subList(0, Math.min(10, historyList.size()));

        //fill single gaps - only needed for Dana*R data
        List<TDD> dummies = (returnDummies != null) ? returnDummies : (new LinkedList());
        DateFormat df = new SimpleDateFormat("dd.MM.");
        for (int i = 0; i < historyList.size() - 1; i++) {
            TDD elem1 = historyList.get(i);
            TDD elem2 = historyList.get(i + 1);

            if (!df.format(new Date(elem1.date)).equals(df.format(new Date(elem2.date + 25 * 60 * 60 * 1000)))) {
                TDD dummy = new TDD();
                dummy.date = elem1.date - 24 * 60 * 60 * 1000;
                dummy.basal = elem1.basal / 2;
                dummy.bolus = elem1.bolus / 2;
                dummies.add(dummy);
                elem1.basal /= 2;
                elem1.bolus /= 2;


            }
        }
        historyList.addAll(dummies);
        Collections.sort(historyList, new Comparator<TDD>() {
            @Override
            public int compare(TDD lhs, TDD rhs) {
                return (int) (rhs.date - lhs.date);
            }
        });

        return historyList;
    }

    @NonNull
    private static String getPumpStatus() {
        return ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(false);
    }

    @NonNull
    private static String getLoopStatus() {
        String ret = "";
        // decide if enabled/disabled closed/open; what Plugin as APS?
        final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
        if (loopPlugin.isEnabled(loopPlugin.getType())) {
            if (MainApp.getConstraintChecker().isClosedLoopAllowed().value()) {
                ret += "CLOSED LOOP\n";
            } else {
                ret += "OPEN LOOP\n";
            }
            final APSInterface aps = ConfigBuilderPlugin.getPlugin().getActiveAPS();
            ret += "APS: " + ((aps == null) ? "NO APS SELECTED!" : ((PluginBase) aps).getName());
            if (LoopPlugin.lastRun != null) {
                if (LoopPlugin.lastRun.lastAPSRun != null)
                    ret += "\nLast Run: " + DateUtil.timeString(LoopPlugin.lastRun.lastAPSRun);

                if (LoopPlugin.lastRun.lastEnact != null)
                    ret += "\nLast Enact: " + DateUtil.timeString(LoopPlugin.lastRun.lastEnact);

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
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null) {
            return "No profile set :(";
        }

        //Check for Temp-Target:
        TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
        if (tempTarget != null) {
            ret += "Temp Target: " + Profile.toTargetRangeString(tempTarget.low, tempTarget.low, Constants.MGDL, profile.getUnits());
            ret += "\nuntil: " + DateUtil.timeString(tempTarget.originalEnd());
            ret += "\n\n";
        }

        ret += "DEFAULT RANGE: ";
        ret += profile.getTargetLow() + " - " + profile.getTargetHigh();
        ret += " target: " + profile.getTarget();
        return ret;
    }

    private static String getOAPSResultStatus() {
        String ret = "";
        if (!Config.APS) {
            return "Only apply in APS mode!";
        }
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null) {
            return "No profile set :(";
        }

        APSInterface usedAPS = ConfigBuilderPlugin.getPlugin().getActiveAPS();
        if (usedAPS == null) {
            return "No active APS :(!";
        }

        APSResult result = usedAPS.getLastAPSResult();
        if (result == null) {
            return "Last result not available!";
        }

        if (!result.isChangeRequested()) {
            ret += MainApp.gs(R.string.nochangerequested) + "\n";
        } else if (result.rate == 0 && result.duration == 0) {
            ret += MainApp.gs(R.string.canceltemp) + "\n";
        } else {
            ret += MainApp.gs(R.string.rate) + ": " + DecimalFormatter.to2Decimal(result.rate) + " U/h " +
                    "(" + DecimalFormatter.to2Decimal(result.rate / ConfigBuilderPlugin.getPlugin().getActivePump().getBaseBasalRate() * 100) + "%)\n" +
                    MainApp.gs(R.string.duration) + ": " + DecimalFormatter.to0Decimal(result.duration) + " min\n";
        }
        ret += "\n" + MainApp.gs(R.string.reason) + ": " + result.reason;

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
            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();
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
        } else if ("wizard2".equals(act[0])) {
            if (lastBolusWizard != null) {
                //use last calculation as confirmed string matches

                doBolus(lastBolusWizard.getCalculatedTotalInsulin(), lastBolusWizard.getCarbs());
                lastBolusWizard = null;
            }
        } else if ("bolus".equals(act[0])) {
            double insulin = SafeParse.stringToDouble(act[1]);
            int carbs = SafeParse.stringToInt(act[2]);
            doBolus(insulin, carbs);
        } else if ("cppset".equals(act[0])) {
            int timeshift = SafeParse.stringToInt(act[1]);
            int percentage = SafeParse.stringToInt(act[2]);
            setCPP(timeshift, percentage);
        } else if ("ecarbs".equals(act[0])) {
            int carbs = SafeParse.stringToInt(act[1]);
            long starttime = SafeParse.stringToLong(act[2]);
            int duration = SafeParse.stringToInt(act[3]);

            doECarbs(carbs, starttime, duration);
        } else if ("dismissoverviewnotification".equals(act[0])) {
            RxBus.INSTANCE.send(new EventDismissNotification(SafeParse.stringToInt(act[1])));
        } else if ("changeRequest".equals(act[0])) {
            LoopPlugin.getPlugin().acceptChangeRequest();
            NotificationManager notificationManager =
                    (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(Constants.notificationID);
        }
        lastBolusWizard = null;
    }

    private static void doECarbs(int carbs, long time, int duration) {
        if (carbs > 0) {
            if (duration == 0) {
                CarbsGenerator.createCarb(carbs, time, CareportalEvent.CARBCORRECTION, "watch");
            } else {
                CarbsGenerator.generateCarbs(carbs, time, duration, "watch eCarbs");
            }
        }
    }

    private static void setCPP(int timeshift, int percentage) {

        String msg = "";


        //check for validity
        if (percentage < Constants.CPP_MIN_PERCENTAGE || percentage > Constants.CPP_MAX_PERCENTAGE) {
            msg += String.format(MainApp.gs(R.string.valueoutofrange), "Profile-Percentage") + "\n";
        }
        if (timeshift < 0 || timeshift > 23) {
            msg += String.format(MainApp.gs(R.string.valueoutofrange), "Profile-Timeshift") + "\n";
        }
        final Profile profile = ProfileFunctions.getInstance().getProfile();

        if (profile == null) {
            msg += MainApp.gs(R.string.notloadedplugins) + "\n";
        }
        if (!"".equals(msg)) {
            msg += MainApp.gs(R.string.valuesnotstored);
            String rTitle = "STATUS";
            String rAction = "statusmessage";
            WearPlugin.getPlugin().requestActionConfirmation(rTitle, msg, rAction);
            lastSentTimestamp = System.currentTimeMillis();
            lastConfirmActionString = rAction;
            return;
        }

        //send profile to pumpe
        new NewNSTreatmentDialog(); //init
        ProfileFunctions.doProfileSwitch(0, percentage, timeshift);
    }

    private static void generateTempTarget(int duration, double low, double high) {
        TempTarget tempTarget = new TempTarget()
                .date(System.currentTimeMillis())
                .duration(duration)
                .reason("WearPlugin")
                .source(Source.USER);
        if (tempTarget.durationInMinutes != 0) {
            tempTarget.low(low).high(high);
        } else {
            tempTarget.low(0).high(0);
        }
        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
    }

    private static void doFillBolus(final Double amount) {
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.insulin = amount;
        detailedBolusInfo.isValid = false;
        detailedBolusInfo.source = Source.USER;
        ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    sendError(MainApp.gs(R.string.treatmentdeliveryerror) +
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
        if (detailedBolusInfo.insulin > 0 || ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().storesCarbInfo) {
            ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        sendError(MainApp.gs(R.string.treatmentdeliveryerror) +
                                "\n" +
                                result.comment);
                    }
                }
            });
        } else {
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
        }
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
