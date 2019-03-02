package info.nightscout.androidaps.plugins.pump.insight.utils.crypto;

public class DerivedKeys {

    byte[] incomingKey;
    byte[] outgoingKey;
    String verificationString;

    public byte[] getIncomingKey() {
        return this.incomingKey;
    }

    public byte[] getOutgoingKey() {
        return this.outgoingKey;
    }

    public String getVerificationString() {
        return this.verificationString;
    }

    public void setIncomingKey(byte[] incomingKey) {
        this.incomingKey = incomingKey;
    }

    public void setOutgoingKey(byte[] outgoingKey) {
        this.outgoingKey = outgoingKey;
    }

    public void setVerificationString(String verificationString) {
        this.verificationString = verificationString;
    }
}
