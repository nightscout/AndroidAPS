package info.nightscout.androidaps.plugins.pump.insight.utils.crypto;

import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.digests.MD5Digest;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.encodings.OAEPEncoding;
import org.spongycastle.crypto.engines.RSAEngine;
import org.spongycastle.crypto.engines.TwofishEngine;
import org.spongycastle.crypto.generators.RSAKeyPairGenerator;
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.params.AsymmetricKeyParameter;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.crypto.params.RSAKeyGenerationParameters;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters;

import java.math.BigInteger;
import java.security.SecureRandom;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class Cryptograph {

    private static final String keySeed = "master secret";
    private static final String verificationSeed = "finished";

    private static byte[] getHmac(byte[] secret, byte[] data, Digest algorithm) {
        HMac hmac = new HMac(algorithm);
        hmac.init(new KeyParameter(secret));
        byte[] result = new byte[hmac.getMacSize()];
        hmac.update(data, 0, data.length);
        hmac.doFinal(result, 0);
        return result;
    }

    private static byte[] getMultiHmac(byte[] secret, byte[] data, int bytes, Digest algorithm) {
        byte[] nuData = data;
        byte[] output = new byte[bytes];
        int size = 0;
        while (size < bytes) {
            nuData = getHmac(secret, nuData, algorithm);
            byte[] preOutput = getHmac(secret, combine(nuData, data), algorithm);
            System.arraycopy(preOutput, 0, output, size, Math.min(bytes - size, preOutput.length));
            size += preOutput.length;
        }
        return output;
    }

    private static byte[] sha1MultiHmac(byte[] secret, byte[] data, int bytes) {
        return getMultiHmac(secret, data, bytes, new SHA1Digest());
    }

    private static byte[] md5MultiHmac(byte[] secret, byte[] data, int bytes) {
        return getMultiHmac(secret, data, bytes, new MD5Digest());
    }

    public static byte[] getServicePasswordHash(String servicePassword, byte[] salt) {
        return multiHashXOR(servicePassword.getBytes(), combine("service pwd".getBytes(), salt), 16);
    }

    private static byte[] byteArrayXOR(byte[] array1, byte[] array2) {
        int length = Math.min(array1.length, array2.length);
        byte[] xor = new byte[length];
        for (int i = 0; i < length; i++) {
            xor[i] = (byte) (array1[i] ^ array2[i]);
        }
        return xor;
    }

    private static byte[] multiHashXOR(byte[] secret, byte[] seed, int bytes) {
        byte[] array1 = new byte[secret.length / 2];
        byte[] array2 = new byte[array1.length];
        System.arraycopy(secret, 0, array1, 0, array1.length);
        System.arraycopy(secret, array1.length, array2, 0, array2.length);
        byte[] md5 = md5MultiHmac(array1, seed, bytes);
        byte[] sha1 = sha1MultiHmac(array2, seed, bytes);
        return byteArrayXOR(md5, sha1);
    }

    public static DerivedKeys deriveKeys(byte[] verificationSeed, byte[] secret, byte[] random, byte[] peerRandom) {
        byte[] result = multiHashXOR(secret, combine(combine(keySeed.getBytes(), random), peerRandom), 32);
        DerivedKeys derivedKeys = new DerivedKeys();
        derivedKeys.incomingKey = new byte[result.length / 2];
        derivedKeys.outgoingKey = new byte[derivedKeys.incomingKey.length];
        System.arraycopy(result, 0, derivedKeys.incomingKey, 0, derivedKeys.incomingKey.length);
        System.arraycopy(result, derivedKeys.incomingKey.length, derivedKeys.outgoingKey, 0, derivedKeys.outgoingKey.length);
        derivedKeys.verificationString = calculateVerificationString(verificationSeed, result);
        return derivedKeys;
    }

    private static String calculateVerificationString(byte[] verificationSeed, byte[] key) {
        byte[] verificationData = multiHashXOR(key, combine(Cryptograph.verificationSeed.getBytes(), verificationSeed), 8);
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            long byteValue = verificationData[i];
            if (byteValue < 0) byteValue += 256;
            value |= byteValue << (i * 8);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int index = 0; index < 10; index++) {
            if (index == 3 || index == 6) stringBuilder.append(" ");
            stringBuilder.append(VerificationString.TABLE[((int) value) & 63]);
            value >>= 6;
        }
        return stringBuilder.toString();
    }

    private static byte[] processRSA(AsymmetricKeyParameter key, byte[] data, boolean encrypt) throws InvalidCipherTextException {
        OAEPEncoding cipher = new OAEPEncoding(new RSAEngine());
        cipher.init(encrypt, key);
        return cipher.processBlock(data, 0, data.length);
    }

    public static byte[] decryptRSA(RSAPrivateCrtKeyParameters key, byte[] data) throws InvalidCipherTextException {
        return processRSA(key, data, false);
    }

    public static KeyPair generateRSAKey() {
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(65537), new SecureRandom(), 2048, 8));
        AsymmetricCipherKeyPair ackp = generator.generateKeyPair();
        KeyPair keyPair = new KeyPair();
        keyPair.privateKey = (RSAPrivateCrtKeyParameters) ackp.getPrivate();
        keyPair.publicKey = (RSAKeyParameters) ackp.getPublic();
        return keyPair;
    }

    public static byte[] combine(byte[] array1, byte[] array2) {
        byte[] combined = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, combined, 0, array1.length);
        System.arraycopy(array2, 0, combined, array1.length, array2.length);
        return combined;
    }

    private static byte[] produceCCMPrimitive(byte headerByte, byte[] nonce, short number) {
        ByteBuf byteBuf = new ByteBuf(16);
        byteBuf.putByte(headerByte);
        byteBuf.putBytes(nonce);
        byteBuf.putShort(number);
        return byteBuf.getBytes();
    }

    private static byte[] produceIV(byte[] nonce, short payloadSize) {
        return produceCCMPrimitive((byte) 0x59, nonce, payloadSize);
    }

    private static byte[] produceCTRBlock(byte[] nonce, short counter) {
        return produceCCMPrimitive((byte) 0x01, nonce, counter);
    }

    private static byte[] blockCipherZeroPad(byte[] input) {
        int modulus = input.length % 16;
        if (modulus == 0) return input;
        byte[] append = new byte[16 - modulus];
        for (int i = 0; i < 16 - modulus; i++) {
            append[i] = 0x00;
        }
        return combine(input, append);
    }

    public static byte[] encryptDataCTR(byte[] data, byte[] key, byte[] nonce) {
        byte[] padded = blockCipherZeroPad(data);
        int length = padded.length >> 4;
        byte[] result = new byte[length * 16];
        TwofishEngine engine = new TwofishEngine();
        engine.init(true, new KeyParameter(key));
        for (int i = 0; i < length; i++) {
            engine.processBlock(produceCTRBlock(nonce, (short) (i + 1)), 0, result, i * 16);
        }
        byte[] xor = byteArrayXOR(padded, result);
        byte[] copy = new byte[Math.min(data.length, xor.length)];
        System.arraycopy(xor, 0, copy, 0, copy.length);
        return copy;
    }

    private static byte[] processHeader(byte[] header) {
        ByteBuf byteBuf;
        byteBuf = new ByteBuf(2 + header.length);
        byteBuf.putShort((short) header.length);
        byteBuf.putBytes(header);
        return byteBuf.getBytes();
    }

    public static byte[] produceCCMTag(byte[] nonce, byte[] payload, byte[] header, byte[] key) {
        TwofishEngine engine = new TwofishEngine();
        engine.init(true, new KeyParameter(key));
        byte[] initializationVector = new byte[engine.getBlockSize()];
        engine.processBlock(produceIV(nonce, (short) payload.length), 0, initializationVector, 0);
        CBCBlockCipher cbc = new CBCBlockCipher(new TwofishEngine());
        cbc.init(true, new ParametersWithIV(new KeyParameter(key), initializationVector));
        byte[] processedHeader = blockCipherZeroPad(processHeader(header));
        byte[] processedPayload = blockCipherZeroPad(payload);
        byte[] combine = combine(processedHeader, blockCipherZeroPad(processedPayload));
        byte[] result = new byte[combine.length];
        for (int i = 0; i < combine.length / 16; i++)
            cbc.processBlock(combine, i * 16, result, i * 16);
        byte[] result2 = new byte[8];
        System.arraycopy(result, result.length - 16, result2, 0, 8);
        byte[] ctr = new byte[engine.getBlockSize()];
        engine.processBlock(produceCTRBlock(nonce, (short) 0), 0, ctr, 0);
        return byteArrayXOR(result2, ctr);
    }

    public static int calculateCRC(byte[] bytes) {
        int crc = 0xffff;
        for (byte b : bytes) {
            crc = (crc >>> 8) ^ CRC.TABLE[(crc ^ b) & 0xff];
        }
        return crc;
    }
}
