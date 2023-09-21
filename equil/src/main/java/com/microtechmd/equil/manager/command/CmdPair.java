package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.EquilConst;
import com.microtechmd.equil.manager.AESUtil;
import com.microtechmd.equil.manager.EquilCmdModel;
import com.microtechmd.equil.manager.EquilResponse;
import com.microtechmd.equil.manager.Utils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import info.nightscout.shared.logging.LTag;

public class CmdPair extends BaseCmd {
    String sn;
    public String address;

    public CmdPair(String name, String address) {
        port = "0E0E";
        this.address = address;
        sn = name.replace("Equil - ", "").trim();
        sn = convertString(sn);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String convertString(String input) {
        StringBuilder sb = new StringBuilder();
        for (char ch : input.toCharArray()) {
            sb.append("0").append(ch);
        }
        return sb.toString();
    }

    @Override
    public EquilResponse getEquilResponse() {
        response = new EquilResponse();
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(Utils.hexStringToBytes(sn));
//            messageDigest.update(new byte[]{0x0A, 0x00, 0x07, 0x0F, 0x06, 0x05});
//            messageDigest.update(new byte[]{0X0A, 0x00, 0x07, 0x0E, 0x0B, 0x0F});
//            0x0a, 0x00, 0x06, 0x0e, 0x0b, 0x0f
//            messageDigest.update(UtilBlue.hexStringToBytes(App.SN));
            byte[] pwd = messageDigest.digest();
            byte[] data = new byte[]{
                    (byte) 0x53, (byte) 0xc9, (byte) 0xA0, (byte) 0x18, (byte) 0xbb, (byte) 0x55, (byte) 0xF0, (byte) 0x9A,
                    (byte) 0xE0, (byte) 0xA7, (byte) 0x4E, (byte) 0x1F, (byte) 0x50, (byte) 0x0D, (byte) 0x28, (byte) 0xE8,
                    (byte) 0xFF, (byte) 0xBB, (byte) 0xC8, (byte) 0x57, (byte) 0xB5, (byte) 0x96, (byte) 0xCA, (byte) 0xC4,
                    (byte) 0x7C, (byte) 0xA9, (byte) 0x51, (byte) 0xDB, (byte) 0x66, (byte) 0xB6, (byte) 0x6D, (byte) 0x1F,
                    (byte) 0x06, (byte) 0x97, (byte) 0x75, (byte) 0x66, (byte) 0x7D, (byte) 0x87, (byte) 0x5A, (byte) 0x2B,
                    (byte) 0x6E, (byte) 0x7C, (byte) 0xDB, (byte) 0xDA, (byte) 0x46, (byte) 0xC0, (byte) 0xF0, (byte) 0x3D,
                    (byte) 0x3A, (byte) 0x51, (byte) 0xD7, (byte) 0xEA, (byte) 0x6F, (byte) 0xEA, (byte) 0x73, (byte) 0x77,
                    (byte) 0x30, (byte) 0x96, (byte) 0x48, (byte) 0xA1, (byte) 0x23, (byte) 0xA2, (byte) 0xD5, (byte) 0x8A};
            EquilCmdModel equilCmdModel = AESUtil.aesEncrypt(pwd, data);

            return responseCmd(equilCmdModel, "0D0D0000");
        } catch (Exception e) {
            e.printStackTrace();
        }


        return null;
    }

    @Override public EquilResponse getNextEquilResponse() {
        return null;
    }


    public EquilResponse decodeEquilPacket(byte[] data) {
        if (!checkData(data)) {
            return null;
        }
        byte code = data[4];
        int intValue = getIndex(code);
        if (config) {
            if (rspIndex == intValue) {
                return null;
            }
            boolean flag = isEnd(code);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            response.add(buffer);
            if (!flag) {
                return null;
            }
            try {
                EquilResponse list = decodeConfirm();
                isEnd = true;
                response = new EquilResponse();
                rspIndex = intValue;
                aapsLogger.debug(LTag.EQUILBLE, "intValue=====" + intValue + "====" + rspIndex);
                return list;
            } catch (Exception e) {
                response = new EquilResponse();
                aapsLogger.error(LTag.EQUILBLE, "decodeConfirm error =====" + e.getMessage());

            }

            return null;
        }
        boolean flag = isEnd(code);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        response.add(buffer);
        if (!flag) {
            return null;
        }
        try {
            EquilResponse list = decode();
            response = new EquilResponse();
            config = true;
            rspIndex = intValue;
            aapsLogger.debug(LTag.EQUILBLE, "intValue=====" + intValue + "====" + rspIndex);
            return list;
        } catch (Exception e) {
            response = new EquilResponse();
            aapsLogger.error(LTag.EQUILBLE, "decode error=====" + e.getMessage());
        }
        return null;

    }

    @Override
    public EquilResponse decode() throws Exception {
        EquilCmdModel equilCmdModel = decodeModel();
        byte[] keyBytes = new byte[]{
                (byte) 0x06, (byte) 0x97, (byte) 0x75, (byte) 0x66, (byte) 0x7D, (byte) 0x87, (byte) 0x5A, (byte) 0x2B,
                (byte) 0x6E, (byte) 0x7C, (byte) 0xDB, (byte) 0xDA, (byte) 0x46, (byte) 0xC0, (byte) 0xF0, (byte) 0x3D,
                (byte) 0x3A, (byte) 0x51, (byte) 0xD7, (byte) 0xEA, (byte) 0x6F, (byte) 0xEA, (byte) 0x73, (byte) 0x77,
                (byte) 0x30, (byte) 0x96, (byte) 0x48, (byte) 0xA1, (byte) 0x23, (byte) 0xA2, (byte) 0xD5, (byte) 0x8A
        };
        String content = AESUtil.decrypt(equilCmdModel, keyBytes);
        String pwd1 = content.substring(0, 64);
        String pwd2 = content.substring(64, content.length());
        sp.putString(EquilConst.Prefs.EQUIL_PASSWORD, pwd2);
        sp.putString(EquilConst.Prefs.EQUIL_DEVICES, pwd1);
        runPwd = pwd2;
        byte[] data1 = Utils.hexStringToBytes(pwd1);
        byte[] data = Utils.concat(data1, keyBytes);
        EquilCmdModel equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
        return responseCmd(equilCmdModel2, port + equilCmdModel2.getCode());
    }


    @Override
    public EquilResponse decodeConfirm() throws Exception {
        EquilCmdModel equilCmdModel = decodeModel();

        String content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd));
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
        return null;
    }
}
