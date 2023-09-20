package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.EquilConst;
import com.microtechmd.equil.manager.Utils;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.shared.logging.LTag;

public class CmdBasalSet extends BaseSetting {
    Profile profile;

    public CmdBasalSet(Profile profile) {
        this.profile = profile;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x01, 0x02};
        List<Byte> list = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double value = profile.getBasalTimeFromMidnight(i * 60 * 60);
            value = value / 2f;
            byte[] bs = intToByteArray(value);
            aapsLogger.error(LTag.EQUILBLE,
                    i + "==CmdBasalSet==" + value + "====" + Utils.bytesToHex(bs));
            list.add(bs[1]);
            list.add(bs[0]);
            list.add(bs[1]);
            list.add(bs[0]);
        }
        String hex = Utils.bytesToHex(list);
        byte[] data = Utils.concat(indexByte, data2, Utils.hexStringToBytes(hex));
        index2++;
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
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x00, 0x02, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        index2++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            sp.putBoolean(EquilConst.Prefs.EQUIL_BASAL_SET, true);
            setCmdStatus(true);
            notify();
        }
    }
}
