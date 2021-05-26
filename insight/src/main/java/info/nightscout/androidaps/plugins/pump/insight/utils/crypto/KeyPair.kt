package info.nightscout.androidaps.plugins.pump.insight.utils.crypto

import org.spongycastle.crypto.params.RSAKeyParameters
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters

class KeyPair {

    //public RSAKeyParameters getPublicKey() {
    @JvmField var privateKey: RSAPrivateCrtKeyParameters? = null
    @JvmField var publicKey: RSAKeyParameters? = null
    val publicKeyBytes: ByteArray
        get() {
            val modulus = publicKey!!.modulus.toByteArray()
            val bytes = ByteArray(256)
            System.arraycopy(modulus, 1, bytes, 0, 256)
            return bytes
        }
    //    return this.publicKey;
    //}
}