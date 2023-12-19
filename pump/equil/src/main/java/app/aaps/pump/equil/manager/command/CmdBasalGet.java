package app.aaps.pump.equil.manager.command;


import java.util.Arrays;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.profile.Profile;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


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
        aapsLogger.debug(LTag.PUMPCOMM, "CmdBasalGet==" + Utils.bytesToHex(data));
        StringBuilder currentBasal = new StringBuilder();
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
        aapsLogger.debug(LTag.PUMPCOMM,
                "CmdBasalGet==" + currentBasal + "====\n==" + rspBasal);
        synchronized (this) {
            setCmdStatus(true);
            setEnacted(currentBasal.toString().equals(rspBasal));
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
