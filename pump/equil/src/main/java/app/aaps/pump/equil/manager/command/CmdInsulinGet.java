package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;

public class CmdInsulinGet extends BaseSetting {

    public CmdInsulinGet() {
        super(System.currentTimeMillis());
        this.port = "0505";

    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x07};
        byte[] data = Utils.concat(indexByte, data2);
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
        int insulin = data[6] & 0xff;
        equilManager.setStartInsulin(insulin);
        equilManager.setCurrentInsulin(insulin);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }

    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
