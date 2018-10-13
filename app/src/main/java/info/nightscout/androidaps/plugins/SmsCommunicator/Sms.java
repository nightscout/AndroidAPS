package info.nightscout.androidaps.plugins.SmsCommunicator;

import android.telephony.SmsMessage;

class Sms {
    String phoneNumber;
    String confirmCode; // move
    String text;
    long date; //move
    boolean received = false;
    boolean sent = false;
    boolean processed = false;

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

    Sms(String phoneNumber, String text) {
        this.phoneNumber = phoneNumber;
        this.text = text;
        sent = true;
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

