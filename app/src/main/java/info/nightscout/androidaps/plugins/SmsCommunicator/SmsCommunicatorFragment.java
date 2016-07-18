package info.nightscout.androidaps.plugins.SmsCommunicator;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.SmsCommunicator.Events.EventNewSMS;
import info.nightscout.utils.SafeParse;

/**
 * A simple {@link Fragment} subclass.
 */
public class SmsCommunicatorFragment extends Fragment implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(SmsCommunicatorFragment.class);

    TextView logView;

    boolean fragmentEnabled = false;
    boolean fragmentVisible = true;

    public SmsCommunicatorFragment() {
        super();
        registerBus();
    }

    public static SmsCommunicatorFragment newInstance() {
        SmsCommunicatorFragment fragment = new SmsCommunicatorFragment();
        return fragment;
    }

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

    ArrayList<Sms> messages = new ArrayList<Sms>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.smscommunicator_fragment, container, false);

        logView = (TextView) view.findViewById(R.id.smscommunicator_log);

        updateGUI();
        return view;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
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
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
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

        if (allowedNumbers.indexOf(receivedSms.phoneNumber) < 0) {
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
                case "RNSC":
                    Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                    MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
                    reply = "RNSC " + q.size() + " receivers";
                    receivedSms.processed = true;
                    break;
                case "DANAR":
                    DanaRFragment danaRFragment = (DanaRFragment) MainApp.getSpecificPlugin(DanaRFragment.class);
                    if (danaRFragment != null) reply = danaRFragment.shortStatus();
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
                        PumpInterface pumpInterface = MainApp.getConfigBuilder().getActivePump();
                        if (pumpInterface != null) {
                            danaRFragment = (DanaRFragment) MainApp.getSpecificPlugin(DanaRFragment.class);
                            PumpEnactResult result = pumpInterface.deliverTreatment(bolusWaitingForConfirmation.bolusRequested, 0);
                            if (result.success) {
                                reply = String.format(MainApp.sResources.getString(R.string.bolusdelivered), bolusWaitingForConfirmation.bolusRequested);
                                if (danaRFragment != null) reply += "\n" + danaRFragment.shortStatus();
                                lastRemoteBolusTime = new Date();
                            } else {
                                reply = MainApp.sResources.getString(R.string.bolusfailed);
                                if (danaRFragment != null) reply += "\n" + danaRFragment.shortStatus();
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
            smsManager.sendTextMessage(newSms.phoneNumber, null, newSms.text, null, null);
            messages.add(newSms);
        }
        updateGUI();
    }


    private void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    class CustomComparator implements Comparator<Sms> {
                        public int compare(Sms object1, Sms object2) {
                            return (int) (object1.date.getTime() - object2.date.getTime());
                        }
                    }
                    Collections.sort(messages, new CustomComparator());
                    int messagesToShow = 40;

                    int start = Math.max(0, messages.size() - messagesToShow);
                    DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);

                    String logText = "";
                    for (int x = start; x < messages.size(); x++) {
                        Sms sms = messages.get(x);
                        if (sms.received) {
                            logText += df.format(sms.date) + " &lt;&lt;&lt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
                        } else if (sms.sent) {
                            logText += df.format(sms.date) + " &gt;&gt;&gt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
                        }
                    }
                    logView.setText(Html.fromHtml(logText));
                }
            });
    }
}
