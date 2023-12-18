package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.AESUtil;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;

public class CmdResistanceGet extends BaseSetting {
    public CmdResistanceGet() {
        super(System.currentTimeMillis());
        port = "1515";
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x02};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x02, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {
        int value = Utils.bytesToInt(data[7], data[6]);
        cmdStatus = true;
        if (value < 500) {
            setEnacted(false);
        } else {
            setEnacted(true);
        }
        synchronized (this) {
            notify();
        }
    }

    public EquilResponse decodeConfirm() throws Exception {
        EquilCmdModel equilCmdModel = decodeModel();
        runCode = equilCmdModel.getCode();
        String content = AESUtil.decrypt(equilCmdModel, Utils.hexStringToBytes(runPwd));
        decodeConfirmData(Utils.hexStringToBytes(content));
        byte[] data = getNextData();
        EquilCmdModel equilCmdModel2 = AESUtil.aesEncrypt(Utils.hexStringToBytes(runPwd), data);
//        return responseCmd(equilCmdModel2, port + runCode,true);
        return null;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
