package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;

public class CmdTmepBasalGet extends BaseSetting {
    public CmdTmepBasalGet() {
        super(System.currentTimeMillis());
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x02};
        byte[] data3 = Utils.intToBytes(120);
        byte[] data4 = Utils.intToBytes(120);
        byte[] data = Utils.concat(indexByte, data2, data3, data4);
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

    public void decodeConfirmData(byte[] data) {

    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
