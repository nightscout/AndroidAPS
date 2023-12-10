package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdModelGet extends BaseSetting {
    public CmdModelGet() {
        super(System.currentTimeMillis());
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x00};
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

        int mode = data[6] & 0xff;
        aapsLogger.debug(LTag.PUMPCOMM, "CmdGetModel====" + mode);
        equilManager.setModel(mode);
        cmdStatus = true;
        synchronized (this) {
            notify();
        }

    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
