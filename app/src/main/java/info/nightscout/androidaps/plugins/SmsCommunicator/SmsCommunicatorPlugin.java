package info.nightscout.androidaps.plugins.SmsCommunicator;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventSmsCommunicatorUpdateGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.T;
import info.nightscout.utils.XdripCalibrations;

/**
 * Created by mike on 05.08.2016.
 */
public class SmsCommunicatorPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(SmsCommunicatorPlugin.class);

    private static SmsCommunicatorPlugin smsCommunicatorPlugin;

    public static SmsCommunicatorPlugin getPlugin() {

        if (smsCommunicatorPlugin == null) {
            smsCommunicatorPlugin = new SmsCommunicatorPlugin();
        }
        return smsCommunicatorPlugin;
    }

    private List<String> allowedNumbers = new ArrayList<>();

    class Sms {
        String phoneNumber;
        String text;
        long date;
        boolean received = false;
        boolean sent = false;
        boolean processed = false;

        String confirmCode;
        double bolusRequested = 0d;
        double tempBasal = 0d;
        double calibrationRequested = 0d;
        int duration = 0;

        Sms(SmsMessage message) {
            phoneNumber = message.getOriginatingAddress();
            text = message.getMessageBody();
            date = message.getTimestampMillis();
            received = true;
        }

        Sms(String phoneNumber, String text, long date) {
            this.phoneNumber = phoneNumber;
            this.text = text;
            this.date = date;
            sent = true;
        }

        Sms(String phoneNumber, String text, long date, String confirmCode) {
            this.phoneNumber = phoneNumber;
            this.text = text;
            this.date = date;
            this.confirmCode = confirmCode;
            sent = true;
        }

        public String toString() {
            return "SMS from " + phoneNumber + ": " + text;
        }
    }

    private Sms cancelTempBasalWaitingForConfirmation = null;
    private Sms tempBasalWaitingForConfirmation = null;
    private Sms bolusWaitingForConfirmation = null;
    private Sms calibrationWaitingForConfirmation = null;
    private Sms suspendWaitingForConfirmation = null;
    private Date lastRemoteBolusTime = new Date(0);

    ArrayList<Sms> messages = new ArrayList<>();

    private SmsCommunicatorPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(SmsCommunicatorFragment.class.getName())
                .pluginName(R.string.smscommunicator)
                .shortName(R.string.smscommunicator_shortname)
                .preferencesId(R.xml.pref_smscommunicator)
                .description(R.string.description_sms_communicator)
        );
        processSettings(null);
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void processSettings(final EventPreferenceChange ev) {
        if (ev == null || ev.isChanged(R.string.key_smscommunicator_allowednumbers)) {
            String settings = SP.getString(R.string.key_smscommunicator_allowednumbers, "");

            String pattern = ";";

            String[] substrings = settings.split(pattern);
            for (String number : substrings) {
                String cleaned = number.replaceAll("\\s+", "");
                allowedNumbers.add(cleaned);
                log.debug("Found allowed number: " + cleaned);
            }
        }
    }

    private boolean isAllowedNumber(String number) {
        for (String num : allowedNumbers) {
            if (num.equals(number)) return true;
        }
        return false;
    }

    public void handleNewData(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus != null) {
            // For every SMS message received
            for (Object pdu : pdus) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                processSms(new Sms(message));
            }
        }
    }

    private void processSms(final Sms receivedSms) {
        if (!isEnabled(PluginType.GENERAL)) {
            log.debug("Ignoring SMS. Plugin disabled.");
            return;
        }
        if (!isAllowedNumber(receivedSms.phoneNumber)) {
            log.debug("Ignoring SMS from: " + receivedSms.phoneNumber + ". Sender not allowed");
            return;
        }

        String reply = "";

        messages.add(receivedSms);
        log.debug(receivedSms.toString());

        String[] splited = receivedSms.text.split("\\s+");
        Double amount;
        Double tempBasal;
        int duration = 0;
        String passCode;
        boolean remoteCommandsAllowed = SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false);

        if (splited.length > 0) {
            switch (splited[0].toUpperCase()) {
                case "BG":
                    BgReading actualBG = DatabaseHelper.actualBg();
                    BgReading lastBG = DatabaseHelper.lastBg();

                    String units = ProfileFunctions.getInstance().getProfileUnits();

                    if (actualBG != null) {
                        reply = MainApp.gs(R.string.sms_actualbg) + " " + actualBG.valueToUnitsToString(units) + ", ";
                    } else if (lastBG != null) {
                        Long agoMsec = System.currentTimeMillis() - lastBG.date;
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        reply = MainApp.gs(R.string.sms_lastbg) + " " + lastBG.valueToUnitsToString(units) + " " + String.format(MainApp.gs(R.string.sms_minago), agoMin) + ", ";
                    }
                    GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
                    if (glucoseStatus != null)
                        reply += MainApp.gs(R.string.sms_delta) + " " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units + ", ";

                    TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
                    IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments().round();
                    TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
                    IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals().round();

                    reply += MainApp.gs(R.string.sms_iob) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                            + MainApp.gs(R.string.sms_bolus) + " " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                            + MainApp.gs(R.string.sms_basal) + " " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";

                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                    receivedSms.processed = true;
                    FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Bg"));
                    break;
                case "LOOP":
                    if (splited.length > 1)
                        switch (splited[1].toUpperCase()) {
                            case "DISABLE":
                            case "STOP":
                                LoopPlugin loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                                if (loopPlugin != null && loopPlugin.isEnabled(PluginType.LOOP)) {
                                    loopPlugin.setPluginEnabled(PluginType.LOOP, false);
                                    ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                                        @Override
                                        public void run() {
                                            MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_STOP"));
                                            String reply = MainApp.gs(R.string.smscommunicator_loophasbeendisabled) + " " +
                                                    MainApp.gs(result.success ? R.string.smscommunicator_tempbasalcanceled : R.string.smscommunicator_tempbasalcancelfailed);
                                            sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                        }
                                    });
                                }
                                receivedSms.processed = true;
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Loop_Stop"));
                                break;
                            case "ENABLE":
                            case "START":
                                loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                                if (loopPlugin != null && !loopPlugin.isEnabled(PluginType.LOOP)) {
                                    loopPlugin.setPluginEnabled(PluginType.LOOP, true);
                                    reply = MainApp.gs(R.string.smscommunicator_loophasbeenenabled);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                    MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_START"));
                                }
                                receivedSms.processed = true;
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Loop_Start"));
                                break;
                            case "STATUS":
                                loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                                if (loopPlugin != null) {
                                    if (loopPlugin.isEnabled(PluginType.LOOP)) {
                                        if (loopPlugin.isSuspended())
                                            reply = String.format(MainApp.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend());
                                        else
                                            reply = MainApp.gs(R.string.smscommunicator_loopisenabled);
                                    } else {
                                        reply = MainApp.gs(R.string.smscommunicator_loopisdisabled);
                                    }
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                                receivedSms.processed = true;
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Loop_Status"));
                                break;
                            case "RESUME":
                                LoopPlugin.getPlugin().suspendTo(0);
                                MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_RESUME"));
                                NSUpload.uploadOpenAPSOffline(0);
                                reply = MainApp.gs(R.string.smscommunicator_loopresumed);
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Loop_Resume"));
                                break;
                            case "SUSPEND":
                                if (splited.length >= 3)
                                    duration = SafeParse.stringToInt(splited[2]);
                                duration = Math.max(0, duration);
                                duration = Math.min(180, duration);
                                if (duration == 0) {
                                    reply = MainApp.gs(R.string.smscommunicator_wrongduration);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                } else if (remoteCommandsAllowed) {
                                    passCode = generatePasscode();
                                    reply = String.format(MainApp.gs(R.string.smscommunicator_suspendreplywithcode), duration, passCode);
                                    receivedSms.processed = true;
                                    resetWaitingMessages();
                                    sendSMS(suspendWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis(), passCode));
                                    suspendWaitingForConfirmation.duration = duration;
                                    FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Loop_Suspend"));
                                } else {
                                    reply = MainApp.gs(R.string.smscommunicator_remotecommandnotallowed);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                                break;
                        }
                    break;
                case "TREATMENTS":
                    if (splited.length > 1)
                        switch (splited[1].toUpperCase()) {
                            case "REFRESH":
                                Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                                TreatmentsPlugin.getPlugin().getService().resetTreatments();
                                MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                                List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                                reply = "TERATMENTS REFRESH " + q.size() + " receivers";
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                receivedSms.processed = true;
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Treatments_Refresh"));
                                break;
                        }
                    break;
                case "NSCLIENT":
                    if (splited.length > 1)
                        switch (splited[1].toUpperCase()) {
                            case "RESTART":
                                Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                                MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                                List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                                reply = "NSCLIENT RESTART " + q.size() + " receivers";
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                receivedSms.processed = true;
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Nsclient_Restart"));
                                break;
                        }
                    break;
                case "PUMP":
                case "DANAR":
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("SMS", new Callback() {
                        @Override
                        public void run() {
                            PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
                            if (result.success) {
                                if (pump != null) {
                                    String reply = pump.shortStatus(true);
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                            } else {
                                String reply = MainApp.gs(R.string.readstatusfailed);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                            }
                        }
                    });
                    receivedSms.processed = true;
                    FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Pump"));
                    break;
                case "BASAL":
                    if (splited.length > 1) {
                        if (splited[1].toUpperCase().equals("CANCEL") || splited[1].toUpperCase().equals("STOP")) {
                            if (remoteCommandsAllowed) {
                                passCode = generatePasscode();
                                reply = String.format(MainApp.gs(R.string.smscommunicator_basalstopreplywithcode), passCode);
                                receivedSms.processed = true;
                                resetWaitingMessages();
                                sendSMS(cancelTempBasalWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis(), passCode));
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Basal"));
                            } else {
                                reply = MainApp.gs(R.string.smscommunicator_remotebasalnotallowed);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                            }
                        } else {
                            tempBasal = SafeParse.stringToDouble(splited[1]);
                            Profile profile = ProfileFunctions.getInstance().getProfile();
                            if (profile == null) {
                                reply = MainApp.gs(R.string.noprofile);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                            } else {
                                tempBasal = MainApp.getConstraintChecker().applyBasalConstraints(new Constraint<>(tempBasal), profile).value();
                                if (remoteCommandsAllowed) {
                                    passCode = generatePasscode();
                                    reply = String.format(MainApp.gs(R.string.smscommunicator_basalreplywithcode), tempBasal, passCode);
                                    receivedSms.processed = true;
                                    resetWaitingMessages();
                                    sendSMS(tempBasalWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis(), passCode));
                                    tempBasalWaitingForConfirmation.tempBasal = tempBasal;
                                    FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Basal"));
                                } else {
                                    reply = MainApp.gs(R.string.smscommunicator_remotebasalnotallowed);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                            }
                        }
                    }
                    break;
                case "BOLUS":
                    if (System.currentTimeMillis() - lastRemoteBolusTime.getTime() < Constants.remoteBolusMinDistance) {
                        reply = MainApp.gs(R.string.smscommunicator_remotebolusnotallowed);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                    } else if (ConfigBuilderPlugin.getPlugin().getActivePump().isSuspended()) {
                        reply = MainApp.gs(R.string.pumpsuspended);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                    } else if (splited.length > 1) {
                        amount = SafeParse.stringToDouble(splited[1]);
                        amount = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(amount)).value();
                        if (amount > 0d && remoteCommandsAllowed) {
                            passCode = generatePasscode();
                            reply = String.format(MainApp.gs(R.string.smscommunicator_bolusreplywithcode), amount, passCode);
                            receivedSms.processed = true;
                            resetWaitingMessages();
                            sendSMS(bolusWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis(), passCode));
                            bolusWaitingForConfirmation.bolusRequested = amount;
                            FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Bolus"));
                        } else {
                            reply = MainApp.gs(R.string.smscommunicator_remotebolusnotallowed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                        }
                    }
                    break;
                case "CAL":
                    if (splited.length > 1) {
                        amount = SafeParse.stringToDouble(splited[1]);
                        if (amount > 0d && remoteCommandsAllowed) {
                            passCode = generatePasscode();
                            reply = String.format(MainApp.gs(R.string.smscommunicator_calibrationreplywithcode), amount, passCode);
                            receivedSms.processed = true;
                            resetWaitingMessages();
                            sendSMS(calibrationWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis(), passCode));
                            calibrationWaitingForConfirmation.calibrationRequested = amount;
                            FabricPrivacy.getInstance().logCustom(new CustomEvent("SMS_Cal"));
                        } else {
                            reply = MainApp.gs(R.string.smscommunicator_remotecalibrationnotallowed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                        }
                    }
                    break;
                default: // expect passCode here
                    if (bolusWaitingForConfirmation != null && !bolusWaitingForConfirmation.processed &&
                            bolusWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - bolusWaitingForConfirmation.date < Constants.SMS_CONFIRM_TIMEOUT) {
                        bolusWaitingForConfirmation.processed = true;
                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                        detailedBolusInfo.insulin = bolusWaitingForConfirmation.bolusRequested;
                        detailedBolusInfo.source = Source.USER;
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                            @Override
                            public void run() {
                                PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
                                if (result.success) {
                                    SystemClock.sleep(T.secs(15).msecs()); // wait some time to get history
                                    String reply = String.format(MainApp.gs(R.string.smscommunicator_bolusdelivered), result.bolusDelivered);
                                    if (pump != null)
                                        reply += "\n" + pump.shortStatus(true);
                                    lastRemoteBolusTime = new Date();
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                } else {
                                    SystemClock.sleep(T.secs(60).msecs()); // wait some time to get history
                                    String reply = MainApp.gs(R.string.smscommunicator_bolusfailed);
                                    if (pump != null)
                                        reply += "\n" + pump.shortStatus(true);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                            }
                        });
                    } else if (tempBasalWaitingForConfirmation != null && !tempBasalWaitingForConfirmation.processed &&
                            tempBasalWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - tempBasalWaitingForConfirmation.date < Constants.SMS_CONFIRM_TIMEOUT) {
                        tempBasalWaitingForConfirmation.processed = true;
                        Profile profile = ProfileFunctions.getInstance().getProfile();
                        if (profile != null)
                            ConfigBuilderPlugin.getPlugin().getCommandQueue().tempBasalAbsolute(tempBasalWaitingForConfirmation.tempBasal, 30, true, profile, new Callback() {
                                @Override
                                public void run() {
                                    if (result.success) {
                                        String reply = String.format(MainApp.gs(R.string.smscommunicator_tempbasalset), result.absolute, result.duration);
                                        reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                        sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                    } else {
                                        String reply = MainApp.gs(R.string.smscommunicator_tempbasalfailed);
                                        reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                        sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                    }
                                }
                            });
                    } else if (cancelTempBasalWaitingForConfirmation != null && !cancelTempBasalWaitingForConfirmation.processed &&
                            cancelTempBasalWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - cancelTempBasalWaitingForConfirmation.date < Constants.SMS_CONFIRM_TIMEOUT) {
                        cancelTempBasalWaitingForConfirmation.processed = true;
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                            @Override
                            public void run() {
                                if (result.success) {
                                    String reply = MainApp.gs(R.string.smscommunicator_tempbasalcanceled);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                } else {
                                    String reply = MainApp.gs(R.string.smscommunicator_tempbasalcancelfailed);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                            }
                        });
                    } else if (calibrationWaitingForConfirmation != null && !calibrationWaitingForConfirmation.processed &&
                            calibrationWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - calibrationWaitingForConfirmation.date < Constants.SMS_CONFIRM_TIMEOUT) {
                        calibrationWaitingForConfirmation.processed = true;
                        boolean result = XdripCalibrations.sendIntent(calibrationWaitingForConfirmation.calibrationRequested);
                        if (result) {
                            reply = MainApp.gs(R.string.smscommunicator_calibrationsent);
                            sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                        } else {
                            reply = MainApp.gs(R.string.smscommunicator_calibrationfailed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                        }
                    } else if (suspendWaitingForConfirmation != null && !suspendWaitingForConfirmation.processed &&
                            suspendWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - suspendWaitingForConfirmation.date < Constants.SMS_CONFIRM_TIMEOUT) {
                        suspendWaitingForConfirmation.processed = true;
                        final int dur =  suspendWaitingForConfirmation.duration;
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                            @Override
                            public void run() {
                                if (result.success) {
                                    LoopPlugin.getPlugin().suspendTo(System.currentTimeMillis() + dur * 60L * 1000);
                                    NSUpload.uploadOpenAPSOffline(dur * 60);
                                    MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_SUSPENDED"));
                                    String reply = MainApp.gs(R.string.smscommunicator_loopsuspended) + " " +
                                            MainApp.gs(result.success ? R.string.smscommunicator_tempbasalcanceled : R.string.smscommunicator_tempbasalcancelfailed);
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                } else {
                                    String reply = MainApp.gs(R.string.smscommunicator_tempbasalcancelfailed);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, System.currentTimeMillis()));
                                }
                            }
                        });
                    } else {
                        sendSMS(new Sms(receivedSms.phoneNumber, MainApp.gs(R.string.smscommunicator_unknowncommand), System.currentTimeMillis()));
                    }
                    resetWaitingMessages();
                    break;
            }
        }

        MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
    }

    public void sendNotificationToAllNumbers(String text) {
        for (int i = 0; i < allowedNumbers.size(); i++) {
            Sms sms = new Sms(allowedNumbers.get(i), text, System.currentTimeMillis());
            sendSMS(sms);
        }
    }

    private void sendSMSToAllNumbers(Sms sms) {
        for (String number : allowedNumbers) {
            sms.phoneNumber = number;
            sendSMS(sms);
        }
    }

    private void sendSMS(Sms sms) {
        SmsManager smsManager = SmsManager.getDefault();
        sms.text = stripAccents(sms.text);
        if (sms.text.length() > 140) sms.text = sms.text.substring(0, 139);
        try {
            log.debug("Sending SMS to " + sms.phoneNumber + ": " + sms.text);
            smsManager.sendTextMessage(sms.phoneNumber, null, sms.text, null, null);
            messages.add(sms);
        } catch (IllegalArgumentException e) {
            Notification notification = new Notification(Notification.INVALID_PHONE_NUMBER, MainApp.gs(R.string.smscommunicator_invalidphonennumber), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
        } catch (java.lang.SecurityException e) {
            Notification notification = new Notification(Notification.MISSING_SMS_PERMISSION, MainApp.gs(R.string.smscommunicator_missingsmspermission), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
        }
    }

    private String generatePasscode() {
        int startChar1 = 'A'; // on iphone 1st char is uppercase :)
        String passCode = Character.toString((char) (startChar1 + Math.random() * ('z' - 'a' + 1)));
        int startChar2 = Math.random() > 0.5 ? 'a' : 'A';
        passCode += Character.toString((char) (startChar2 + Math.random() * ('z' - 'a' + 1)));
        int startChar3 = Math.random() > 0.5 ? 'a' : 'A';
        passCode += Character.toString((char) (startChar3 + Math.random() * ('z' - 'a' + 1)));
        return passCode;
    }

    private void resetWaitingMessages() {
        tempBasalWaitingForConfirmation = null;
        cancelTempBasalWaitingForConfirmation = null;
        bolusWaitingForConfirmation = null;
        calibrationWaitingForConfirmation = null;
        suspendWaitingForConfirmation = null;
    }

    private static String stripAccents(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return s;
    }
}
