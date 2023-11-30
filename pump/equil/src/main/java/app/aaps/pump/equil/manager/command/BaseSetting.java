package app.aaps.pump.equil.manager.command;

import java.nio.ByteBuffer;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.manager.AESUtil;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;


public abstract class BaseSetting extends BaseCmd {

    public BaseSetting(long createTime) {
        super(createTime);
    }

    public byte[] getReqData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] tzm = Utils.hexStringToBytes(getEquilDevices());
        byte[] data = Utils.concat(indexByte, tzm);
        pumpReqIndex++;
        return data;
    }


    @Override
    public EquilResponse getEquilResponse() {
        config = false;
        isEnd = false;
        response = new EquilResponse(createTime);
        byte[] pwd = Utils.hexStringToBytes(getEquilPassWord());
        byte[] data = getReqData();
        EquilCmdModel equilCmdModel;
        try {
            equilCmdModel = AESUtil.aesEncrypt(pwd, data);
            return responseCmd(equilCmdModel, defaultPort + "0000");
        } catch (Exception e) {
            synchronized (this) {
                setCmdStatus(false);
            }
        }
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
                EquilResponse response1 = decodeConfirm();
                isEnd = true;
                response = new EquilResponse(createTime);
                rspIndex = intValue;
                return response1;
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
        EquilCmdModel reqModel = decodeModel();
        byte[] pwd = Utils.hexStringToBytes(getEquilPassWord());
        String content = AESUtil.decrypt(reqModel, pwd);
        String pwd2 = content.substring(8);
        runPwd = pwd2;
        byte[] data = getFirstData();
        EquilCmdModel equilCmdModel = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
        runCode = reqModel.getCode();
        return responseCmd(equilCmdModel, port + runCode);
    }

    public EquilResponse getNextEquilResponse() {
        aapsLogger.debug(LTag.PUMPCOMM, "getNextEquilResponse=== start11 ");
        config = true;
        isEnd = false;
        response = new EquilResponse(createTime);
        byte[] data = getFirstData();
        EquilCmdModel equilCmdModel;
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "getNextEquilResponse=== start ");
            equilCmdModel = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
            return responseCmd(equilCmdModel, port + runCode);
        } catch (Exception e) {
            aapsLogger.debug(LTag.PUMPCOMM, "getNextEquilResponse===" + e.getMessage());
            synchronized (this) {
                setCmdStatus(false);
            }
        }
        return null;
    }


    public abstract byte[] getFirstData();

    public abstract byte[] getNextData();

    public abstract void decodeConfirmData(byte[] data);

    public EquilResponse decodeConfirm() throws Exception {
        EquilCmdModel equilCmdModel = decodeModel();
        runCode = equilCmdModel.getCode();
        String content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd));
        decodeConfirmData(Utils.hexStringToBytes(content));
        byte[] data = getNextData();
        EquilCmdModel equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
        return responseCmd(equilCmdModel2, port + runCode);
    }
}
