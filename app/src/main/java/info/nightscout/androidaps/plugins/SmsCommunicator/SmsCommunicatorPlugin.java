package info.nightscout.androidaps.plugins.SmsCommunicator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

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
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventNewSMS;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventSmsCommunicatorUpdateGui;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 05.08.2016.
 */
public class SmsCommunicatorPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(SmsCommunicatorPlugin.class);

    private static boolean fragmentEnabled = false;
    private static boolean fragmentVisible = true;

    final long CONFIRM_TIMEOUT = 5 * 60 * 1000L;

    public class Sms {
        String phoneNumber;
        String text;
        Date date;
        boolean received = false;
        boolean sent = false;
        boolean processed = false;

        String confirmCode;
        double bolusRequested = 0d;
        double tempBasal = 0d;

        public Sms(SmsMessage message) {
            phoneNumber = message.getOriginatingAddress();
            text = message.getMessageBody();
            date = new Date(message.getTimestampMillis());
            received = true;
        }

        public Sms(String phoneNumber, String text, Date date) {
            this.phoneNumber = phoneNumber;
            this.text = text;
            this.date = date;
            sent = true;
        }

        public String toString() {
            return "SMS from " + phoneNumber + ": " + text;
        }
    }

    Sms cancelTempBasalWaitingForConfirmation = null;
    Sms tempBasalWaitingForConfirmation = null;
    Sms bolusWaitingForConfirmation = null;
    Date lastRemoteBolusTime = new Date(0);

    ArrayList<Sms> messages = new ArrayList<>();

    public SmsCommunicatorPlugin() {
        MainApp.bus().register(this);
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
    }

    @Subscribe
    public void onStatusEvent(final EventNewSMS ev) {

        Object[] pdus = (Object[]) ev.bundle.get("pdus");
        // For every SMS message received
        for (int i = 0; i < pdus.length; i++) {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdus[i]);
            processSms(new Sms(message));
        }
    }

    private void processSms(Sms receivedSms) {
        if (!isEnabled(PluginBase.GENERAL)) {
            log.debug("Ignoring SMS. Plugin disabled.");
            return;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String allowedNumbers = sharedPreferences.getString("smscommunicator_allowednumbers", "");

        if (!allowedNumbers.contains(receivedSms.phoneNumber)) {
            log.debug("Ignoring SMS from: " + receivedSms.phoneNumber + ". Sender not allowed");
            return;
        }

        String reply = "";

        messages.add(receivedSms);
        log.debug(receivedSms.toString());

        String[] splited = receivedSms.text.split("\\s+");
        Double amount = 0d;
        Double tempBasal = 0d;
        String passCode = "";
        Sms newSms = null;

        if (splited.length > 0) {
            switch (splited[0].toUpperCase()) {
                case "LOOP":
                    switch (splited[1].toUpperCase()) {
                        case "STOP":
                            LoopPlugin loopPlugin = (LoopPlugin) MainApp.getSpecificPlugin(LoopPlugin.class);
                            if (loopPlugin != null && loopPlugin.isEnabled(PluginBase.LOOP)) {
                                loopPlugin.setFragmentEnabled(PluginBase.LOOP, false);
                                reply = MainApp.sResources.getString(R.string.smscommunicator_loophasbeendisabled);
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            }
                            receivedSms.processed = true;
                            break;
                        case "START":
                            loopPlugin = (LoopPlugin) MainApp.getSpecificPlugin(LoopPlugin.class);
                            if (loopPlugin != null && !loopPlugin.isEnabled(PluginBase.LOOP)) {
                                loopPlugin.setFragmentEnabled(PluginBase.LOOP, true);
                                reply = MainApp.sResources.getString(R.string.smscommunicator_loophasbeenenabled);
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            }
                            receivedSms.processed = true;
                            break;
                        case "STATUS":
                            loopPlugin = (LoopPlugin) MainApp.getSpecificPlugin(LoopPlugin.class);
                            if (loopPlugin != null) {
                                if (loopPlugin.isEnabled(PluginBase.LOOP)) {
                                    reply = MainApp.sResources.getString(R.string.smscommunicator_loopisenabled);
                                } else {
                                    reply = MainApp.sResources.getString(R.string.smscommunicator_loopisdisabled);
                                }
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            }
                            receivedSms.processed = true;
                            break;
                    }
                    break;
                case "TREATMENTS":
                    switch (splited[1].toUpperCase()) {
                        case "REFRESH":
                            Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                            MainApp.getDbHelper().resetTreatments();
                            MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                            List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                            reply = "TERATMENTS REFRESH " + q.size() + " receivers";
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            receivedSms.processed = true;
                            break;
                    }
                    break;
                case "NSCLIENT":
                    switch (splited[1].toUpperCase()) {
                        case "RESTART":
                            Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                            MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                            List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                            reply = "NSCLIENT RESTART " + q.size() + " receivers";
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            receivedSms.processed = true;
                            break;
                    }
                    break;
                case "DANAR":
                    DanaRPlugin danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                    if (danaRPlugin != null && danaRPlugin.isEnabled(PluginBase.PUMP))
                        reply = danaRPlugin.shortStatus();
                        newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                    receivedSms.processed = true;
                    break;
                case "BASAL":
                    if (splited.length > 1) {
                        boolean remoteCommandsAllowed = sharedPreferences.getBoolean("smscommunicator_remotecommandsallowed", false);
                        if (splited[1].toUpperCase().equals("CANCEL") || splited[1].toUpperCase().equals("STOP")) {
                            if (remoteCommandsAllowed) {
                                passCode = generatePasscode();
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_basalstopreplywithcode), passCode);
                                receivedSms.processed = true;
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                                newSms.confirmCode = passCode;
                                resetWaitingMessages();
                                cancelTempBasalWaitingForConfirmation = newSms;
                            } else {
                                reply = MainApp.sResources.getString(R.string.remotebasalnotallowed);
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            }
                        } else {
                            tempBasal = SafeParse.stringToDouble(splited[1]);
                            tempBasal = MainApp.getConfigBuilder().applyBasalConstraints(tempBasal);
                            if (remoteCommandsAllowed) {
                                passCode = generatePasscode();
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_basalreplywithcode), tempBasal, passCode);
                                receivedSms.processed = true;
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                                newSms.tempBasal = tempBasal;
                                newSms.confirmCode = passCode;
                                resetWaitingMessages();
                                tempBasalWaitingForConfirmation = newSms;
                            } else {
                                reply = MainApp.sResources.getString(R.string.remotebasalnotallowed);
                                newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            }
                        }
                    }
                    break;
                case "BOLUS":
                    if (new Date().getTime() - lastRemoteBolusTime.getTime() < Constants.remoteBolusMinDistance) {
                        reply = MainApp.sResources.getString(R.string.remotebolusnotallowed);
                        newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                    } else if (splited.length > 1) {
                        amount = SafeParse.stringToDouble(splited[1]);
                        amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
                        boolean remoteCommandsAllowed = sharedPreferences.getBoolean("smscommunicator_remotecommandsallowed", false);
                        if (amount > 0d && remoteCommandsAllowed) {
                            passCode = generatePasscode();
                            reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_bolusreplywithcode), amount, passCode);
                            receivedSms.processed = true;
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                            newSms.bolusRequested = amount;
                            newSms.confirmCode = passCode;
                            resetWaitingMessages();
                            bolusWaitingForConfirmation = newSms;
                        } else {
                            reply = MainApp.sResources.getString(R.string.remotebolusnotallowed);
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                        }
                    }
                    break;
                default: // expect passCode here
                    if (bolusWaitingForConfirmation != null && !bolusWaitingForConfirmation.processed &&
                            bolusWaitingForConfirmation.confirmCode.equals(splited[0]) && new Date().getTime() - bolusWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        bolusWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                            PumpEnactResult result = pumpInterface.deliverTreatment(bolusWaitingForConfirmation.bolusRequested, 0, null);
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.bolusdelivered), result.bolusDelivered);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                                lastRemoteBolusTime = new Date();
                            } else {
                                reply = MainApp.sResources.getString(R.string.bolusfailed);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                            }
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                        }
                    } else if (tempBasalWaitingForConfirmation != null && !tempBasalWaitingForConfirmation.processed &&
                            tempBasalWaitingForConfirmation.confirmCode.equals(splited[0]) && new Date().getTime() - tempBasalWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        tempBasalWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                            PumpEnactResult result = pumpInterface.setTempBasalAbsolute(tempBasalWaitingForConfirmation.tempBasal, 30);
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_tempbasalset), result.absolute, result.duration);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_tempbasalfailed);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                            }
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                        }
                    } else if (cancelTempBasalWaitingForConfirmation != null && !cancelTempBasalWaitingForConfirmation.processed &&
                            cancelTempBasalWaitingForConfirmation.confirmCode.equals(splited[0]) && new Date().getTime() - cancelTempBasalWaitingForConfirmation.date.getTime() < CONFIRM_TIMEOUT) {
                        cancelTempBasalWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                            PumpEnactResult result = pumpInterface.cancelTempBasal();
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.smscommunicator_tempbasalcanceled));
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                            } else {
                                reply = MainApp.sResources.getString(R.string.smscommunicator_tempbasalcancelfailed);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                            }
                            newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
                        }
                    } else {
                        newSms = new Sms(receivedSms.phoneNumber, MainApp.sResources.getString(R.string.smscommunicator_unknowncommand), new Date());
                    }
                    resetWaitingMessages();
                    break;
            }
        }

        if (newSms != null) {
            SmsManager smsManager = SmsManager.getDefault();
            newSms.text = stripAccents(newSms.text);
            if (newSms.text.length() > 140) newSms.text = newSms.text.substring(0, 139);
            smsManager.sendTextMessage(newSms.phoneNumber, null, newSms.text, null, null);
            messages.add(newSms);
        }
        MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
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
    }

    public static String stripAccents(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return s;
    }
}
