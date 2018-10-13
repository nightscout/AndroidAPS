package info.nightscout.androidaps.plugins.SmsCommunicator;

abstract class SmsAction implements Runnable {
    Double d;
    Integer i;

    SmsAction()  {}

    SmsAction(Double d) {
        this.d = d;
    }

    SmsAction(Integer i) {
        this.i = i;
    }
}
