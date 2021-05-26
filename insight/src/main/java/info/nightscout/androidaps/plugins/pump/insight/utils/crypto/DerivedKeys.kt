package info.nightscout.androidaps.plugins.pump.insight.utils.crypto

class DerivedKeys {

    lateinit var incomingKey: ByteArray
    lateinit var outgoingKey: ByteArray

    //public void setIncomingKey(byte[] incomingKey) {
    @JvmField var verificationString: String? = null
    //    this.incomingKey = incomingKey;
    //}
    //public void setOutgoingKey(byte[] outgoingKey) {
    //    this.outgoingKey = outgoingKey;
    //}
    //public void setVerificationString(String verificationString) {
    //    this.verificationString = verificationString;
    //}
}