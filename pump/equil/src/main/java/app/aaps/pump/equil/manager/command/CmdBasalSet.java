package app.aaps.pump.equil.manager.command;


import java.util.ArrayList;
import java.util.List;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.profile.Profile;
import app.aaps.pump.equil.EquilConst;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.driver.definition.BasalSchedule;
import app.aaps.pump.equil.driver.definition.BasalScheduleEntry;
import app.aaps.pump.equil.manager.Utils;


public class CmdBasalSet extends BaseSetting {
    Profile profile;

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    BasalSchedule basalSchedule;

    public CmdBasalSet(BasalSchedule basalSchedule, Profile profile) {
        super(System.currentTimeMillis());
        this.profile = profile;
        this.basalSchedule = basalSchedule;

    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x02};
        List<Byte> list = new ArrayList<>();
        int i = 0;
        for (BasalScheduleEntry basalScheduleEntry : basalSchedule.getEntries()) {
            double rate = basalScheduleEntry.getRate();
            double value = rate / 2f;
            byte[] bs = Utils.basalToByteArray(value);
            aapsLogger.debug(LTag.PUMPCOMM,
                    i + "==CmdBasalSet==" + value + "====" + rate + "===" + Utils.decodeSpeedToUH(value) + "==="
                            + Utils.decodeSpeedToUHT(value));
            list.add(bs[1]);
            list.add(bs[0]);
            list.add(bs[1]);
            list.add(bs[0]);
            i++;
        }
        String hex = Utils.bytesToHex(list);
        byte[] data = Utils.concat(indexByte, data2, Utils.hexStringToBytes(hex));
        pumpReqIndex++;
        return data;
    }

    public static byte[] intToByteArray(double v) {
        int value = (int) (v / 0.025f * 4);
        byte[] result = new byte[2];
        result[0] = (byte) ((value >> 8) & 0xFF); // 高位
        result[1] = (byte) (value & 0xFF); // 低位
        return result;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x02, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            sp.putBoolean(EquilConst.Prefs.INSTANCE.getEQUIL_BASAL_SET(), true);
            setCmdStatus(true);
            notifyAll();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return EquilHistoryRecord.EventType.SET_BASAL_PROFILE;
    }
}
