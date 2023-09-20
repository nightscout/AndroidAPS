package com.microtechmd.equil.manager;


import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {
    //加密
    public static EquilCmdModel aesEncrypt(byte[] pwd, byte[] data) throws Exception {
        byte[] iv = Utils.hexStringToBytes("364484e1086a8ee504e09ddf");
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
        equilcmdmodel.setIv("364484e1086a8ee504e09ddf");
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