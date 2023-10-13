package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.manager.Utils;

import info.nightscout.shared.logging.LTag;

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
        aapsLogger.debug(LTag.EQUILBLE, "CmdGetModel====" + mode);
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
