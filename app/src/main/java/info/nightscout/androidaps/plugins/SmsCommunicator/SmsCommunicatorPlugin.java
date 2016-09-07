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
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;
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

    public class Sms {
        String phoneNumber;
        String text;
        Date date;
        boolean received = false;
        boolean sent = false;
        boolean processed = false;

        String confirmCode;
        double bolusRequested = 0d;

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
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        SmsCommunicatorPlugin.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        SmsCommunicatorPlugin.fragmentVisible = fragmentVisible;
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
        double amount = 0d;
        String passCode = "";

        if (splited.length > 0) {
            switch (splited[0].toUpperCase()) {
                case "RT":
                    Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                    MainApp.getDbHelper().resetTreatments();
                    MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                    reply = "RT " + q.size() + " receivers";
                    receivedSms.processed = true;
                    break;
                case "RNSC":
                    restartNSClient = new Intent(Intents.ACTION_RESTART);
                    MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                    reply = "RNSC " + q.size() + " receivers";
                    receivedSms.processed = true;
                    break;
                case "DANAR":
                    DanaRPlugin danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                    if (danaRPlugin != null) reply = danaRPlugin.shortStatus();
                    receivedSms.processed = true;
                    break;
                case "BOLUS":
                    if (new Date().getTime() - lastRemoteBolusTime.getTime() < Constants.remoteBolusMinDistance) {
                        reply = MainApp.sResources.getString(R.string.remotebolusnotallowed);
                    } else if (splited.length > 1) {
                        amount = SafeParse.stringToDouble(splited[1]);
                        amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
                        boolean remoteBolusingAllowed = sharedPreferences.getBoolean("smscommunicator_remotebolusingallowed", false);
                        if (amount > 0d && remoteBolusingAllowed) {
                            int startChar1 = 'A'; // on iphone 1st char is uppercase :)
                            passCode = Character.toString((char) (startChar1 + Math.random() * ('z' - 'a' + 1)));
                            int startChar2 = Math.random() > 0.5 ? 'a' : 'A';
                            passCode += Character.toString((char) (startChar2 + Math.random() * ('z' - 'a' + 1)));
                            int startChar3 = Math.random() > 0.5 ? 'a' : 'A';
                            passCode += Character.toString((char) (startChar3 + Math.random() * ('z' - 'a' + 1)));
                            reply = String.format(MainApp.sResources.getString(R.string.replywithcode), amount, passCode);
                            receivedSms.processed = true;
                        } else {
                            reply = MainApp.sResources.getString(R.string.remotebolusnotallowed);
                        }
                    }
                    break;
                default: // expect passCode here
                    if (bolusWaitingForConfirmation != null && !bolusWaitingForConfirmation.processed &&
                            bolusWaitingForConfirmation.confirmCode.equals(splited[0]) && new Date().getTime() - bolusWaitingForConfirmation.date.getTime() < 5 * 60 * 1000L) {
                        bolusWaitingForConfirmation.processed = true;
                        PumpInterface pumpInterface = MainApp.getConfigBuilder();
                        if (pumpInterface != null) {
                            danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                            PumpEnactResult result = pumpInterface.deliverTreatment(bolusWaitingForConfirmation.bolusRequested, 0, null);
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.bolusdelivered), bolusWaitingForConfirmation.bolusRequested);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                                lastRemoteBolusTime = new Date();
                            } else {
                                reply = MainApp.sResources.getString(R.string.bolusfailed);
                                if (danaRPlugin != null) reply += "\n" + danaRPlugin.shortStatus();
                            }
                        }
                    }
                    break;
            }
        }

        if (!reply.equals("")) {
            SmsManager smsManager = SmsManager.getDefault();
            Sms newSms = new Sms(receivedSms.phoneNumber, reply, new Date());
            if (amount > 0d) {
                newSms.bolusRequested = amount;
                newSms.confirmCode = passCode;
                bolusWaitingForConfirmation = newSms;
            } else {
                bolusWaitingForConfirmation = null;
                newSms.processed = true;
            }
            smsManager.sendTextMessage(newSms.phoneNumber, null, stripAccents(newSms.text), null, null);
            messages.add(newSms);
        }
        MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
    }

    public static String stripAccents(String s)
    {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return s;
    }
}
