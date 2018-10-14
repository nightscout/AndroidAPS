package info.nightscout.androidaps.plugins.SmsCommunicator;

import android.telephony.SmsMessage;

import info.nightscout.utils.DateUtil;

class Sms {
    String phoneNumber;
    String text;
    long date;
    boolean received = false;
    boolean sent = false;
    boolean processed = false;

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

    public String toString() {
        return "SMS from " + phoneNumber + ": " + text;
    }
}

