package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.manager.Utils;

import info.nightscout.shared.logging.LTag;

public class CmdInsulinChange extends BaseSetting {

    public CmdInsulinChange() {
        super(System.currentTimeMillis());
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x06};
        byte[] data3 = Utils.intToBytes(32000);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x06, 0x01};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
//        byte[] byteData = Crc.hexStringToBytes(data);
        int status = data[6] & 0xff;
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
        equilManager.setInsulinChange(status);
        aapsLogger.debug(LTag.EQUILBLE, "status====" + status + "====" + Utils.bytesToHex(data));
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return EquilHistoryRecord.EventType.CHANGE_INSULIN;
    }
}
