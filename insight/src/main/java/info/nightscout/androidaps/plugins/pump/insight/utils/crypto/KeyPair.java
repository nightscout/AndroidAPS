package info.nightscout.androidaps.plugins.pump.insight.utils.crypto;

import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters;

public class KeyPair {

    protected KeyPair() {
    }

    RSAPrivateCrtKeyParameters privateKey;
    RSAKeyParameters publicKey;

    public byte[] getPublicKeyBytes() {
        byte[] modulus = publicKey.getModulus().toByteArray();
        byte[] bytes = new byte[256];
        System.arraycopy(modulus, 1, bytes, 0, 256);
        return bytes;
    }

    public RSAPrivateCrtKeyParameters getPrivateKey() {
        return this.privateKey;
    }

    public RSAKeyParameters getPublicKey() {
        return this.publicKey;
    }
}
