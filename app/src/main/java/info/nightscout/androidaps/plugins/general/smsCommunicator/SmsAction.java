package info.nightscout.androidaps.plugins.general.smsCommunicator;

abstract class SmsAction implements Runnable {
    Double aDouble;
    Integer anInteger;
    Integer secondInteger;
    String aString;

    SmsAction()  {}

    SmsAction(Double aDouble) {
        this.aDouble = aDouble;
    }

    SmsAction(Double aDouble, Integer secondInteger) {
        this.aDouble = aDouble;
        this.secondInteger = secondInteger;
    }

    SmsAction(String aString, Integer secondInteger) {
        this.aString = aString;
        this.secondInteger = secondInteger;
    }

    SmsAction(Integer anInteger) {
        this.anInteger = anInteger;
    }

    SmsAction(Integer anInteger, Integer secondInteger) {
        this.anInteger = anInteger;
        this.secondInteger = secondInteger;
    }
}
