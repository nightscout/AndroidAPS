package app.aaps.pump.insight.utils.crypto

import org.spongycastle.crypto.params.RSAKeyParameters
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters

class KeyPair {

    lateinit var privateKey: RSAPrivateCrtKeyParameters
    lateinit var publicKey: RSAKeyParameters
    val publicKeyBytes: ByteArray
        get() {
            val modulus = publicKey.modulus.toByteArray()
            val bytes = ByteArray(256)
            System.arraycopy(modulus, 1, bytes, 0, 256)
            return bytes
        }
}