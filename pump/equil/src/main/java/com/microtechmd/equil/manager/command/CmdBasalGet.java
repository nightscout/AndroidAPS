package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.manager.Utils;

import java.util.Arrays;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.profile.Profile;


public class CmdBasalGet extends BaseSetting {

    Profile profile;

    public CmdBasalGet(Profile profile) {
        super(System.currentTimeMillis());
        this.profile = profile;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x02};
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

    public void decodeConfirmData(byte[] data) {
        aapsLogger.debug(LTag.EQUILBLE, "CmdBasalGet==" + Utils.bytesToHex(data));
        int start = 6;
        StringBuffer currentBasal = new StringBuffer();
        if (profile != null) {
            for (int i = 0; i < 24; i++) {
                double value = profile.getBasalTimeFromMidnight(i * 60 * 60);
                value = value / 2f;
                byte[] bs = Utils.basalToByteArray2(value);
                currentBasal.append(Utils.bytesToHex(bs));
                currentBasal.append(Utils.bytesToHex(bs));
            }
        }
        byte[] rspByte = Arrays.copyOfRange(data, 6, data.length);
        String rspBasal = Utils.bytesToHex(rspByte);
        aapsLogger.debug(LTag.EQUILBLE,
                "CmdBasalGet==" + currentBasal.toString() + "====\n==" + rspBasal);
        synchronized (this) {
            setCmdStatus(true);
            if (currentBasal.toString().equals(rspBasal)) {
                setEnacted(true);
            } else {
                setEnacted(false);
            }
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
