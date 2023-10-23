package app.aaps.pump.equil.manager;


import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import app.aaps.core.interfaces.logging.LTag;


public class AESUtil {
    private static final byte[] AES_KEY = {
            0x0C, 0x02, 0x09, 0x04, 0x14, 0x06, 0x16, 0x08,
            0x19, 0x0C, 0x1B, 0x1C, 0x06, 0x1E, 0x16, 0x20,
            0x11, 0x0C, 0x13, 0x0B, 0x15, 0x08, 0x17, 0x18,
            0x09, 0x1B, 0x0B, 0x0C, 0x06, 0x0E, 0x1C, 0x10

    };

    private static byte[] generateAESKeyFromPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] inputBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] hashBytes = digest.digest(inputBytes);
            byte[] extractedBytes = new byte[16];
            System.arraycopy(hashBytes, 2, extractedBytes, 0, 16);
            return extractedBytes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] getEquilPassWord(String password) {
        String plaintextDefault = "Equil"; // 6位的输入字符串
        byte[] defaultKey = generateAESKeyFromPassword(plaintextDefault);
        byte[] aesKey = Utils.concat(defaultKey, generateAESKeyFromPassword(password));
        Log.e(LTag.EQUILBLE.toString(), Utils.bytesToHex(aesKey) + "===" + aesKey.length);
        return aesKey;
    }

    public static byte[] generateRandomIV(int length) {
        try {
            SecureRandom secureRandom = new SecureRandom();
            byte[] ivBytes = new byte[length];
            secureRandom.nextBytes(ivBytes);
            return ivBytes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //加密
    public static EquilCmdModel aesEncrypt(byte[] pwd, byte[] data) throws Exception {
        byte[] iv = generateRandomIV(12);
        SecretKey key = new SecretKeySpec(pwd, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
        // 加密数据
        byte[] ciphertext = cipher.doFinal(data);
        byte[] authenticationTag = Arrays.copyOfRange(ciphertext, ciphertext.length - 16, ciphertext.length);
        byte[] encryptedData = Arrays.copyOfRange(ciphertext, 0, ciphertext.length - 16);
        EquilCmdModel equilcmdmodel = new EquilCmdModel();
        equilcmdmodel.setTag(Utils.bytesToHex(authenticationTag));
        equilcmdmodel.setIv(Utils.bytesToHex(iv));
        equilcmdmodel.setCiphertext(Utils.bytesToHex(encryptedData));
        return equilcmdmodel;
    }


    public static String decrypt(EquilCmdModel equilCmdModel, byte[] keyBytes) throws Exception {
        try {
            String iv = equilCmdModel.getIv();
            String ciphertext = equilCmdModel.getCiphertext();
            String authenticationTag = equilCmdModel.getTag();
            byte[] ivBytes = Utils.hexStringToBytes(iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            byte[] decodedCiphertext = Utils.hexStringToBytes(ciphertext);
            byte[] decodedAuthenticationTag = Utils.hexStringToBytes(authenticationTag);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            cipher.update(decodedCiphertext);
            byte[] decrypted = cipher.doFinal(decodedAuthenticationTag);
            String content = Utils.bytesToHex(decrypted);
            return content;
        } catch (Exception e) {
            throw e;
        }

    }


}