package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.manager.AESUtil;
import com.microtechmd.equil.manager.EquilCmdModel;
import com.microtechmd.equil.manager.EquilResponse;
import com.microtechmd.equil.manager.Utils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import info.nightscout.shared.logging.LTag;

public class CmdUnPair extends BaseCmd {
    String sn;
    String password;

    public CmdUnPair(String name, String password) {
        super(System.currentTimeMillis());
        port = "0E0E";
        this.password = password;
        sn = name.replace("Equil - ", "").trim();
        sn = convertString(sn);
    }

    byte[] randomPassword;

    public EquilResponse clear1() {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(Utils.hexStringToBytes(sn));
            byte[] pwd = messageDigest.digest();
//            byte[] data = new byte[]{
//                    (byte) 0x53, (byte) 0xc9, (byte) 0xA0, (byte) 0x18, (byte) 0xbb, (byte) 0x55, (byte) 0xF0, (byte) 0x9A,
//                    (byte) 0xE0, (byte) 0xA7, (byte) 0x4E, (byte) 0x1F, (byte) 0x50, (byte) 0x0D, (byte) 0x28, (byte) 0xE8,
//                    (byte) 0xFF, (byte) 0xBB, (byte) 0xC8, (byte) 0x57, (byte) 0xB5, (byte) 0x96, (byte) 0xCA, (byte) 0xC4,
//                    (byte) 0x7C, (byte) 0xA9, (byte) 0x51, (byte) 0xDB, (byte) 0x66, (byte) 0xB6, (byte) 0x6D, (byte) 0x1F,
//                    (byte) 0x06, (byte) 0x97, (byte) 0x75, (byte) 0x66, (byte) 0x7D, (byte) 0x87, (byte) 0x5A, (byte) 0x2B,
//                    (byte) 0x6E, (byte) 0x7C, (byte) 0xDB, (byte) 0xDA, (byte) 0x46, (byte) 0xC0, (byte) 0xF0, (byte) 0x3D,
//                    (byte) 0x3A, (byte) 0x51, (byte) 0xD7, (byte) 0xEA, (byte) 0x6F, (byte) 0xEA, (byte) 0x73, (byte) 0x77,
//                    (byte) 0x30, (byte) 0x96, (byte) 0x48, (byte) 0xA1, (byte) 0x23, (byte) 0xA2, (byte) 0xD5, (byte) 0x8A};

            byte[] equilPassword = AESUtil.getEquilPassWord(password);
            randomPassword = Utils.generateRandomPassword(32);
            byte[] data = Utils.concat(equilPassword, randomPassword);
            EquilCmdModel equilCmdModel = AESUtil.aesEncrypt(pwd, data);
            return responseCmd(equilCmdModel, "0D0D0000");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    @Override
    public EquilResponse getEquilResponse() {
        response = new EquilResponse(createTime);
        return clear1();
    }

    @Override public EquilResponse getNextEquilResponse() {
        return getEquilResponse();
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
                response = new EquilResponse(createTime);
                rspIndex = intValue;
                return list;
            } catch (Exception e) {
                response = new EquilResponse(createTime);
                aapsLogger.debug(LTag.EQUILBLE, "decodeEquilPacket error =====" + e.getMessage());

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
            response = new EquilResponse(createTime);
            config = true;
            rspIndex = intValue;
            return list;
        } catch (Exception e) {
            response = new EquilResponse(createTime);
            aapsLogger.debug(LTag.EQUILBLE, "decodeEquilPacket error=====" + e.getMessage());
        }
        return null;

    }

    @Override
    public EquilResponse decode() throws Exception {
        EquilCmdModel equilCmdModel = decodeModel();
        byte[] keyBytes = randomPassword;


        byte[] data2 = new byte[]{
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        String content = AESUtil.decrypt(equilCmdModel, keyBytes);
        String pwd1 = content.substring(0, 64);
        String pwd2 = content.substring(64, content.length());
        runPwd = pwd2;
        byte[] data1 = Utils.hexStringToBytes(pwd1);
        byte[] data = Utils.concat(data1, data2);

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

    @Override public EquilHistoryRecord.EventType getEventType() {
        return EquilHistoryRecord.EventType.UNPAIR_EQUIL;
    }
}
