package info.nightscout.androidaps.plugins.SmsCommunicator;

abstract class SmsAction implements Runnable {
    Double aDouble;
    Integer anInteger;

    SmsAction()  {}

    SmsAction(Double aDouble) {
        this.aDouble = aDouble;
    }

    SmsAction(Integer anInteger) {
        this.anInteger = anInteger;
    }
}
