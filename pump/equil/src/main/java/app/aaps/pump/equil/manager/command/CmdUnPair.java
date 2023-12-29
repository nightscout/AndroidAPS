package app.aaps.pump.equil.manager.command;


import java.nio.ByteBuffer;
import java.security.MessageDigest;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.AESUtil;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;


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
                aapsLogger.debug(LTag.PUMPCOMM, "decodeEquilPacket error =====" + e.getMessage());

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
            aapsLogger.debug(LTag.PUMPCOMM, "decodeEquilPacket error=====" + e.getMessage());
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
        String pwd2 = content.substring(64);
        runPwd = pwd2;
        byte[] data1 = Utils.hexStringToBytes(pwd1);
        byte[] data = Utils.concat(data1, data2);

        EquilCmdModel equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
        runCode = equilCmdModel.getCode();
        return responseCmd(equilCmdModel2, port + runCode);
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
