package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdTempBasalSet extends BaseSetting {
    double insulin;
    int duration;
    int step;
    int pumpTime;
    boolean cancel;

    public double getInsulin() {
        return insulin;
    }

    public void setInsulin(double insulin) {
        this.insulin = insulin;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean isCancel() {
        return cancel;
    }

    public void setCancel(boolean cancel) {
        this.cancel = cancel;
    }

    public CmdTempBasalSet(double insulin, int duration) {
        super(System.currentTimeMillis());
        this.insulin = insulin;
        this.duration = duration;
        if (insulin != 0) {
            step = (int) (insulin / 0.05d * 8) / 2;
        } else {
            step = 0;
        }
        this.pumpTime = duration * 60;
    }

    @Override
    public byte[] getFirstData() {
        aapsLogger.debug(LTag.PUMPCOMM, "step===" + step + "=====" + pumpTime);
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x04};
        byte[] data3 = Utils.intToBytes(step);
        byte[] data4 = Utils.intToBytes(pumpTime);
        byte[] data = Utils.concat(indexByte, data2, data3, data4);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x04, 0x01};
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
            return EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL;
        } else {
            return EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL;
        }
    }
}
