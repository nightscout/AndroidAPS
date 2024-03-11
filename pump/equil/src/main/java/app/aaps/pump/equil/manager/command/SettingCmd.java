package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;

public class SettingCmd extends BaseSetting {
    public SettingCmd() {
        super(System.currentTimeMillis());
    }


    public byte[] getData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] tzm = Utils.hexStringToBytes(getEquilDevices());
        byte[] data = Utils.concat(indexByte, tzm);
        pumpReqIndex++;
        return data;
    }


    public byte[] getData2() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x07};
        byte[] data3 = Utils.intToBytes(120);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    @Override public byte[] getFirstData() {
        return new byte[0];
    }

    @Override public byte[] getNextData() {
        return new byte[0];
    }

    @Override public void decodeConfirmData(byte[] data) {

    }

    public byte[] getData3() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x07, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
