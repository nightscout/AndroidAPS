package app.aaps.pump.equil.manager.command;


import android.util.Log;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.EquilConst;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.AESUtil;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;


public class CmdPair extends BaseCmd {
    public static final String ERROR_PWD =
            "0000000000000000000000000000000000000000000000000000000000000000";
    String sn;
    public String address;
    private final String password;

    public CmdPair(String name, String address, String password) {
        super(System.currentTimeMillis());
        port = "0E0E";
        this.address = address;
        this.password = password;
        sn = name.replace("Equil - ", "").trim();
        sn = convertString(sn);
        Log.e(LTag.PUMPCOMM.toString(), "sn===" + sn);
    }

    public String getAddress() {
        return address;
    }

    byte[] randomPassword;

    @Override
    public EquilResponse getEquilResponse() {
        response = new EquilResponse(createTime);
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(Utils.hexStringToBytes(sn));
            byte[] pwd = messageDigest.digest();

            //B0EB6308060F79D685D6269DC048E32E4C103CD2B8EEA2DE4637EB8A5D6BCD08
            byte[] equilPassword = AESUtil.getEquilPassWord(password);


            randomPassword = Utils.generateRandomPassword(32);
            byte[] data = Utils.concat(equilPassword, randomPassword);
            aapsLogger.debug(LTag.PUMPCOMM, "pwd==" + Utils.bytesToHex(pwd));
            aapsLogger.debug(LTag.PUMPCOMM, "data==" + Utils.bytesToHex(data));
            EquilCmdModel equilCmdModel = AESUtil.aesEncrypt(pwd, data);
            return responseCmd(equilCmdModel, "0D0D0000");
        } catch (Exception e) {
            e.printStackTrace();
        }


        return null;
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
                aapsLogger.debug(LTag.PUMPCOMM, "intValue=====" + intValue + "====" + rspIndex);
                return list;
            } catch (Exception e) {
                response = new EquilResponse(createTime);
                aapsLogger.debug(LTag.PUMPCOMM, "decodeConfirm error =====" + e.getMessage());

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
            aapsLogger.debug(LTag.PUMPCOMM, "intValue=====" + intValue + "====" + rspIndex);
            return list;
        } catch (Exception e) {
            response = new EquilResponse(createTime);
            aapsLogger.debug(LTag.PUMPCOMM, "decode error=====" + e.getMessage());
        }
        return null;

    }

    @Override
    public EquilResponse decode() throws Exception {
        EquilCmdModel equilCmdModel = decodeModel();
        byte[] keyBytes = randomPassword;
        String content = AESUtil.decrypt(equilCmdModel, keyBytes);
        String pwd1 = content.substring(0, 64);
        String pwd2 = content.substring(64);
        aapsLogger.debug(LTag.PUMPCOMM, "decrypted====" + pwd1);
        aapsLogger.debug(LTag.PUMPCOMM, "decrypted====" + pwd2);
        if (ERROR_PWD.equals(pwd1) && ERROR_PWD.equals(pwd2)) {
            synchronized (this) {
                setCmdStatus(true);
                setEnacted(false);
                notifyAll();
            }
            return null;
        }

        sp.putString(EquilConst.Prefs.INSTANCE.getEQUIL_PASSWORD(), pwd2);
        sp.putString(EquilConst.Prefs.INSTANCE.getEQUIL_DEVICES(), pwd1);
        runPwd = pwd2;
        byte[] data1 = Utils.hexStringToBytes(pwd1);
        byte[] data = Utils.concat(data1, keyBytes);
        EquilCmdModel equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
        runCode = equilCmdModel.getCode();
        return responseCmd(equilCmdModel2, port + runCode);
    }


    @Override
    public EquilResponse decodeConfirm() {
        synchronized (this) {
            setCmdStatus(true);
            notifyAll();
        }
        return null;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return EquilHistoryRecord.EventType.INITIALIZE_EQUIL;
    }
}
