package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;
import app.aaps.pump.equil.manager.Utils;

public class CmdExtendedBolusSet extends BaseSetting {
    double insulin;
    int step;
    int pumpTime;
    int durationInMinutes;
    boolean cancel;

    public boolean isCancel() {
        return cancel;
    }

    public void setCancel(boolean cancel) {
        this.cancel = cancel;
    }

    public double getInsulin() {
        return insulin;
    }

    public void setInsulin(double insulin) {
        this.insulin = insulin;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public CmdExtendedBolusSet(double insulin, int durationInMinutes, boolean cancel) {
        super(System.currentTimeMillis());
        this.insulin = insulin;
        this.durationInMinutes = durationInMinutes;
        this.cancel = cancel;
        if (insulin != 0) {
            step = (int) (insulin / 0.05d * 8);
            this.pumpTime = durationInMinutes * 60;
        }
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x03};
        byte[] data3 = Utils.intToBytes(step);
        byte[] data4 = Utils.intToBytes(pumpTime);
        byte[] data5 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data5, data5, data3, data4);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x03, 0x01};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        int index = data[4];
        int status = data[6] & 0xff;
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        if (cancel) {
            return EquilHistoryRecord.EventType.CANCEL_EXTENDED_BOLUS;
        } else {
            return EquilHistoryRecord.EventType.SET_EXTENDED_BOLUS;
        }
    }
}
