package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.data.RunMode;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;

public class CmdModelSet extends BaseSetting {
    public RunMode getMode() {
        if (mode == 0) {
            return RunMode.SUSPEND;
        } else if (mode == 1) {
            return RunMode.RUN;
        } else if (mode == 2) {
            return RunMode.RUN;
        } else {
            return RunMode.SUSPEND;
        }
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    int mode;

    public CmdModelSet(int mode) {
        super(System.currentTimeMillis());
        this.mode = mode;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x00};
        byte[] data3 = Utils.intToBytes(mode);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x00, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        RunMode runMode = getMode();
        if (runMode == RunMode.RUN) {
            return EquilHistoryRecord.EventType.RESUME_DELIVERY;
        } else if (runMode == RunMode.SUSPEND) {
            return EquilHistoryRecord.EventType.SUSPEND_DELIVERY;
        }
        return null;
    }
}
