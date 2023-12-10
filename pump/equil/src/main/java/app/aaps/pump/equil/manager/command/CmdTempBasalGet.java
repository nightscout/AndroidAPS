package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdTempBasalGet extends BaseSetting {
    private int time;
    private int step;

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public CmdTempBasalGet() {
        super(System.currentTimeMillis());
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x04};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x04, 0x02};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        int index = data[4];
        step = Utils.bytes2Int(new byte[]{data[6], data[7], data[8], data[9]});
        time = Utils.bytes2Int(new byte[]{data[10], data[11], data[12], data[13]});
        aapsLogger.debug(LTag.PUMPCOMM, "CmdTempBasalGet===" + step + "====" + time);
//        Utils.by
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
