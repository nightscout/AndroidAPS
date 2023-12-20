package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.AESUtil;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;

public class CmdStepSet extends BaseSetting {
    boolean sendConfig;
    int step;

    public CmdStepSet(boolean sendConfig, int step) {
        super(System.currentTimeMillis());
        this.sendConfig = sendConfig;
        this.step = step;
    }


    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x07};
        byte[] data3 = Utils.intToBytes(step);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x07, 0x01};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
//        byte[] byteData = Crc.hexStringToBytes(data);
//        int status = byteData[6] & 0xff;
        synchronized (this) {
            setCmdStatus(true);
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
        if (sendConfig) {
            return responseCmd(equilCmdModel2, port + runCode);
        }
        return null;
    }
    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
