package info.nightscout.androidaps.plugins.SmsCommunicator;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import com.crashlytics.android.answers.Answers;
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
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventNewSMS;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventSmsCommunicatorUpdateGui;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.XdripCalibrations;

/**
 * Created by mike on 05.08.2016.
 */
public class SmsCommunicatorPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(SmsCommunicatorPlugin.class);

    private static boolean fragmentEnabled = false;
    private static boolean fragmentVisible = true;

    private final long CONFIRM_TIMEOUT = 5 * 60 * 1000L;

    private List<String> allowedNumbers = new ArrayList<String>();

    class Sms {
        String phoneNumber;
        String text;
        Date date;
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
            date = new Date(message.getTimestampMillis());
            received = true;
        }

        Sms(String phoneNumber, String text, Date date) {
            this.phoneNumber = phoneNumber;
            this.text = text;
            this.date = date;
            sent = true;
        }

        Sms(String phoneNumber, String text, Date date, String confirmCode) {
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

    public SmsCommunicatorPlugin() {
        MainApp.bus().register(this);
        processSettings(null);
    }

    @Override
    public String getFragmentClass() {
        return SmsCommunicatorFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.smscommunicator);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.smscommunicator_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
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

    @Subscribe
    public void onStatusEvent(final EventNewSMS ev) {

        Object[] pdus = (Object[]) ev.bundle.get("pdus");
        // For every SMS message received
        for (Object pdu : pdus) {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
            processSms(new Sms(message));
        }
    }

    private void processSms(Sms receivedSms) {
        if (!isEnabled(PluginBase.GENERAL)) {
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
        Double amount = 0d;
        Double tempBasal = 0d;
        int duration = 0;
        String passCode = "";
        boolean remoteCommandsAllowed = SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false);

        if (splited.length > 0) {
            switch (splited[0].toUpperCase()) {
                case "BG":
                    BgReading actualBG = DatabaseHelper.actualBg();
                    BgReading lastBG = DatabaseHelper.lastBg();

                    String units = MainApp.getConfigBuilder().getProfileUnits();

                    if (actualBG != null) {
                        reply = MainApp.sResources.getString(R.string.sms_actualbg) + " " + actualBG.valueToUnitsToString(units) + ", ";
                    } else if (lastBG != null) {
                        Long agoMsec = System.currentTimeMillis() - lastBG.date;
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        reply = MainApp.sResources.getString(R.string.sms_lastbg) + " " + lastBG.valueToUnitsToString(units) + " " + String.format(MainApp.sResources.getString(R.string.sms_minago), agoMin) + ", ";
                    }
                    GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
                    if (glucoseStatus != null)
                        reply += MainApp.sResources.getString(R.string.sms_delta) + " " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units + ", ";

                    MainApp.getConfigBuilder().updateTotalIOBTreatments();
                    IobTotal bolusIob = MainApp.getConfigBuilder().getLastCalculationTreatments().round();
                    MainApp.getConfigBuilder().updateTotalIOBTempBasals();
                    IobTotal basalIob = MainApp.getConfigBuilder().getLastCalculationTempBasals().round();

                    reply += MainApp.sResources.getString(R.string.sms_iob) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                            + MainApp.sResources.getString(R.string.sms_bolus) + " " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                            + MainApp.sResources.getString(R.string.sms_basal) + " " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";

                    sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                    receivedSms.processed = true;
                    Answers.getInstance().logCustom(new CustomEvent("SMS_Bg"));
                    break;
                case "LOOP":
                    if (splited.length > 1)
                        switch (splited[1].toUpperCase()) {
                            case "DISABLE":
                            case "STOP":
                                LoopPlugin loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                                if (loopPlugin != null && loopPlugin.isEnabled(PluginBase.LOOP)) {
                                    loopPlugin.setFragmentEnabled(PluginBase.LOOP, false);
                                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
                                    MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_STOP"));
                                    reply = MainApp.sResources.getString(R.string.smscommunicator_loophasbeendisabled)+ " " +
                                            MainApp.sResources.getString(result.success?R.string.smscommunicator_tempbasalcanceled:R.string.smscommunicator_tempbasalcancelfailed);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                }
                                receivedSms.processed = true;
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Loop_Stop"));
                                break;
                            case "ENABLE":
                            case "START":
                                loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                                if (loopPlugin != null && !loopPlugin.isEnabled(PluginBase.LOOP)) {
                                    loopPlugin.setFragmentEnabled(PluginBase.LOOP, true);
                                    reply = MainApp.sResources.getString(R.string.smscommunicator_loophasbeenenabled);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                    MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_START"));
                                }
                                receivedSms.processed = true;
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Loop_Start"));
                                break;
                            case "STATUS":
                                loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                                if (loopPlugin != null) {
                                    if (loopPlugin.isEnabled(PluginBase.LOOP)) {
                                        if (loopPlugin.isSuspended())
                                            reply = String.format(MainApp.sResources.getString(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend());
                                        else
                                            reply = MainApp.sResources.getString(R.string.smscommunicator_loopisenabled);
                                    } else {
                                        reply = MainApp.sResources.getString(R.string.smscommunicator_loopisdisabled);
                                    }
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                }
                                receivedSms.processed = true;
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Loop_Status"));
                                break;
                            case "RESUME":
                                final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
                                activeloop.suspendTo(0);
                                MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_RESUME"));
                                NSUpload.uploadOpenAPSOffline(0);
                                reply = MainApp.sResources.getString(R.string.smscommunicator_loopresumed);
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Loop_Resume"));
                                break;
                            case "SUSPEND":
                                if (splited.length >= 3)
                                    duration = SafeParse.stringToInt(splited[2]);
                                duration = Math.max(0, duration);
                                duration = Math.min(180, duration);
                                if (duration == 0) {
                                    reply = MainApp.sResources.getString(R.string.smscommunicator_wrongduration);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                } else if (remoteCommandsAllowed) {
                                    passCode = generatePasscode();
                                    reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_suspendreplywithcode), duration, passCode);
                                    receivedSms.processed = true;
                                    resetWaitingMessages();
                                    sendSMS(suspendWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, new Date(), passCode));
                                    suspendWaitingForConfirmation.duration = duration;
                                    Answers.getInstance().logCustom(new CustomEvent("SMS_Loop_Suspend"));
                                } else {
                                    reply = MainApp.sResources.getString(R.string.smscommunicator_remotecommandnotallowed);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                }
                                break;
                        }
                    break;
                case "TREATMENTS":
                    if (splited.length > 1)
                        switch (splited[1].toUpperCase()) {
                            case "REFRESH":
                                Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                                MainApp.getDbHelper().resetTreatments();
                                MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                                List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                                reply = "TERATMENTS REFRESH " + q.size() + " receivers";
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                receivedSms.processed = true;
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Treatments_Refresh"));
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
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                                receivedSms.processed = true;
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Nsclient_Restart"));
                                break;
                        }
                    break;
                case "DANAR":
                    DanaRPlugin danaRPlugin = MainApp.getSpecificPlugin(DanaRPlugin.class);
                    if (danaRPlugin != null && danaRPlugin.isEnabled(PluginBase.PUMP)) {
                        reply = danaRPlugin.shortStatus(true);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                    }
                    DanaRKoreanPlugin danaRKoreanPlugin = MainApp.getSpecificPlugin(DanaRKoreanPlugin.class);
                    if (danaRKoreanPlugin != null && danaRKoreanPlugin.isEnabled(PluginBase.PUMP)) {
                        reply = danaRKoreanPlugin.shortStatus(true);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                    }
                    receivedSms.processed = true;
                    Answers.getInstance().logCustom(new CustomEvent("SMS_Danar"));
                    break;
                case "BASAL":
                    if (splited.length > 1) {
                        if (splited[1].toUpperCase().equals("CANCEL") || splited[1].toUpperCase().equals("STOP")) {
                            if (remoteCommandsAllowed) {
                                passCode = generatePasscode();
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_basalstopreplywithcode), passCode);
                                receivedSms.processed = true;
                                resetWaitingMessages();
                                sendSMS(cancelTempBasalWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, new Date(), passCode));
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Basal"));
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_remotebasalnotallowed);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            }
                        } else {
                            tempBasal = SafeParse.stringToDouble(splited[1]);
                            tempBasal = MainApp.getConfigBuilder().applyBasalConstraints(tempBasal);
                            if (remoteCommandsAllowed) {
                                passCode = generatePasscode();
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_basalreplywithcode), tempBasal, passCode);
                                receivedSms.processed = true;
                                resetWaitingMessages();
                                sendSMS(tempBasalWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, new Date(), passCode));
                                tempBasalWaitingForConfirmation.tempBasal = tempBasal;
                                Answers.getInstance().logCustom(new CustomEvent("SMS_Basal"));
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_remotebasalnotallowed);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            }
                        }
                    }
                    break;
                case "BOLUS":
                    if (System.currentTimeMillis() - lastRemoteBolusTime.getTime() < Constants.remoteBolusMinDistance) {
                        reply = MainApp.sResources.getString(R.string.smscommunicator_remotebolusnotallowed);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                    } else if (MainApp.getConfigBuilder().isSuspended()) {
                        reply = MainApp.sResources.getString(R.string.pumpsuspended);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                    } else if (splited.length > 1) {
                        amount = SafeParse.stringToDouble(splited[1]);
                        amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
                        if (amount > 0d && remoteCommandsAllowed) {
                            passCode = generatePasscode();
                            reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_bolusreplywithcode), amount, passCode);
                            receivedSms.processed = true;
                            resetWaitingMessages();
                            sendSMS(bolusWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, new Date(), passCode));
                            bolusWaitingForConfirmation.bolusRequested = amount;
                            Answers.getInstance().logCustom(new CustomEvent("SMS_Bolus"));
                        } else {
                            reply = MainApp.sResources.getString(R.string.smscommunicator_remotebolusnotallowed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                        }
                    }
                    break;
                case "CAL":
                    if (splited.length > 1) {
                        amount = SafeParse.stringToDouble(splited[1]);
                        if (amount > 0d && remoteCommandsAllowed) {
                            passCode = generatePasscode();
                            reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_calibrationreplywithcode), amount, passCode);
                            receivedSms.processed = true;
                            resetWaitingMessages();
                            sendSMS(calibrationWaitingForConfirmation = new Sms(receivedSms.phoneNumber, reply, new Date(), passCode));
                            calibrationWaitingForConfirmation.calibrationRequested = amount;
                            Answers.getInstance().logCustom(new CustomEvent("SMS_Cal"));
                        } else {
                            reply = MainApp.sResources.getString(R.string.smscommunicator_remotecalibrationnotallowed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                        }
                    }
                    break;
                default: // expect passCode here
                    if (bolusWaitingForConfirmation != null && !bolusWaitingForConfirmation.processed &&
                            bolusWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - bolusWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        bolusWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = MainApp.getSpecificPlugin(DanaRPlugin.class);
                            DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                            detailedBolusInfo.insulin = bolusWaitingForConfirmation.bolusRequested;
                            detailedBolusInfo.source = Source.USER;
                            PumpEnactResult result = pumpInterface.deliverTreatment(detailedBolusInfo);
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_bolusdelivered), result.bolusDelivered);
                                if (danaRPlugin != null)
                                    reply += "\n" + danaRPlugin.shortStatus(true);
                                lastRemoteBolusTime = new Date();
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_bolusfailed);
                                if (danaRPlugin != null)
                                    reply += "\n" + danaRPlugin.shortStatus(true);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            }
                        }
                    } else if (tempBasalWaitingForConfirmation != null && !tempBasalWaitingForConfirmation.processed &&
                            tempBasalWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - tempBasalWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        tempBasalWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = MainApp.getSpecificPlugin(DanaRPlugin.class);
                            PumpEnactResult result = pumpInterface.setTempBasalAbsolute(tempBasalWaitingForConfirmation.tempBasal, 30, false);
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_tempbasalset), result.absolute, result.duration);
                                if (danaRPlugin != null)
                                    reply += "\n" + danaRPlugin.shortStatus(true);
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_tempbasalfailed);
                                if (danaRPlugin != null)
                                    reply += "\n" + danaRPlugin.shortStatus(true);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            }
                        }
                    } else if (cancelTempBasalWaitingForConfirmation != null && !cancelTempBasalWaitingForConfirmation.processed &&
                            cancelTempBasalWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - cancelTempBasalWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        cancelTempBasalWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = MainApp.getSpecificPlugin(DanaRPlugin.class);
                            PumpEnactResult result = pumpInterface.cancelTempBasal(true);
                            if (result.success) {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_tempbasalcanceled);
                                if (danaRPlugin != null)
                                    reply += "\n" + danaRPlugin.shortStatus(true);
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_tempbasalcancelfailed);
                                if (danaRPlugin != null)
                                    reply += "\n" + danaRPlugin.shortStatus(true);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                            }
                        }
                    } else if (calibrationWaitingForConfirmation != null && !calibrationWaitingForConfirmation.processed &&
                            calibrationWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - calibrationWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        calibrationWaitingForConfirmation.processed = true;
                        boolean result = XdripCalibrations.sendIntent(calibrationWaitingForConfirmation.calibrationRequested);
                        if (result) {
                            reply = MainApp.sResources.getString(R.string.smscommunicator_calibrationsent);
                            sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, new Date()));
                        } else {
                            reply = MainApp.sResources.getString(R.string.smscommunicator_calibrationfailed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply, new Date()));
                        }
                    } else if (suspendWaitingForConfirmation != null && !suspendWaitingForConfirmation.processed &&
                            suspendWaitingForConfirmation.confirmCode.equals(splited[0]) && System.currentTimeMillis() - suspendWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        suspendWaitingForConfirmation.processed = true;
                        final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
                        activeloop.suspendTo(System.currentTimeMillis() + suspendWaitingForConfirmation.duration * 60L * 1000);
                        PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
                        NSUpload.uploadOpenAPSOffline(suspendWaitingForConfirmation.duration * 60);
                        MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_SUSPENDED"));
                        reply = MainApp.sResources.getString(R.string.smscommunicator_loopsuspended) + " " +
                                MainApp.sResources.getString(result.success?R.string.smscommunicator_tempbasalcanceled:R.string.smscommunicator_tempbasalcancelfailed);
                        sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply, new Date()));
                    } else {
                        sendSMS(new Sms(receivedSms.phoneNumber, MainApp.sResources.getString(R.string.smscommunicator_unknowncommand), new Date()));
                    }
                    resetWaitingMessages();
                    break;
            }
        }

        MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
    }

    public void sendNotificationToAllNumbers(String text) {
        for (int i = 0; i < allowedNumbers.size(); i++) {
            Sms sms = new Sms(allowedNumbers.get(i), text, new Date());
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
            Notification notification = new Notification(Notification.INVALID_PHONE_NUMBER, MainApp.sResources.getString(R.string.smscommunicator_invalidphonennumber), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
        } catch (java.lang.SecurityException e) {
            Notification notification = new Notification(Notification.MISSING_SMS_PERMISSION, MainApp.sResources.getString(R.string.smscommunicator_missingsmspermission), Notification.NORMAL);
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
