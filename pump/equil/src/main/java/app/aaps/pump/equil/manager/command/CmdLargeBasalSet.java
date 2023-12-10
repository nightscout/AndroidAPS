package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdLargeBasalSet extends BaseSetting {
    double insulin;
    int step;
    int stepTime;

    public int getStepTime() {
        return stepTime;
    }

    public double getInsulin() {
        return insulin;
    }

    public void setInsulin(double insulin) {
        this.insulin = insulin;
    }

    public CmdLargeBasalSet(double insulin) {
        super(System.currentTimeMillis());
        this.insulin = insulin;
        if (insulin != 0) {
            step = (int) (insulin / 0.05d * 8);
            stepTime = (int) (insulin / 0.05d * 2);
        }
    }

    @Override
    public byte[] getFirstData() {
        aapsLogger.debug(LTag.PUMPCOMM, "step===" + step + "=====" + stepTime);
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x03};
        byte[] data3 = Utils.intToBytes(step);
        byte[] data4 = Utils.intToBytes(stepTime);
        byte[] data5 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3, data4, data5, data5);
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
        if (insulin == 0) {
            return EquilHistoryRecord.EventType.CANCEL_BOLUS;
        } else {
            return EquilHistoryRecord.EventType.SET_BOLUS;
        }
    }
}
