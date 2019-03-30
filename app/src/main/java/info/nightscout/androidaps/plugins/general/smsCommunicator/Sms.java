package info.nightscout.androidaps.plugins.general.smsCommunicator;

import android.telephony.SmsMessage;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.utils.DateUtil;

class Sms {
    String phoneNumber;
    String text;
    long date;
    boolean received = false;
    boolean sent = false;
    boolean processed = false;
    boolean ignored = false;

    Sms(SmsMessage message) {
        phoneNumber = message.getOriginatingAddress();
        text = message.getMessageBody();
        date = message.getTimestampMillis();
        received = true;
    }

    Sms(String phoneNumber, String text) {
        this.phoneNumber = phoneNumber;
        this.text = text;
        this.date = DateUtil.now();
        sent = true;
    }

    Sms(String phoneNumber, int textId) {
        this.phoneNumber = phoneNumber;
        this.text = MainApp.gs(textId);
        this.date = DateUtil.now();
        sent = true;
    }

    public String toString() {
        return "SMS from " + phoneNumber + ": " + text;
    }
}

