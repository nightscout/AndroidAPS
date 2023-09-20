package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

import info.nightscout.shared.logging.LTag;

public class CmdSettingSet extends BaseSetting {
    double lowAlarm;
    public CmdSettingSet(double lowAlarm){
        this.lowAlarm=lowAlarm;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x01, 0x05};
        byte[] data3 = Utils.intToBytes(0);
        byte[] lowAlarmByte = Utils.intToTwoBytes(Utils.decodeSpeedToUH(lowAlarm));
        byte[] fast = Utils.intToTwoBytes(0);

        byte[] data5 = Utils.intToTwoBytes(2800);
        byte[] data6 = Utils.intToTwoBytes(8);
        byte[] data7 = Utils.intToTwoBytes(240);
        byte[] data8 = Utils.intToTwoBytes(1600);
        byte[] data = Utils.concat(indexByte, data2, data3, data3,
                lowAlarmByte, fast,
                data5, data6, data7, data8);
        index2++;

        aapsLogger.error(LTag.EQUILBLE,
                "CmdSettingSet data===" + Utils.bytesToHex(data)+"===="+lowAlarm+"==="+Utils.decodeSpeedToUH(lowAlarm));
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x00, 0x05, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        index2++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        int index = data[4];
//        int i1 = ((data[15] & 0x0f) << 8) | data[14] & 0xff;
//        int i2 = ((data[17] & 0x0f) << 8) | data[16] & 0xff;
//        int i3 = ((data[19] & 0x0f) << 8) | data[18] & 0xff;
//        int i4 = ((data[21] & 0x0f) << 8) | data[20] & 0xff;
//        int i5 = ((data[23] & 0x0f) << 8) | data[22] & 0xff;
//        int i6 = ((data[25] & 0x0f) << 8) | data[24] & 0xff;
//        lowAlarm = Utils.internalDecodeSpeedToUH(i1);
//        largefastAlarm = Utils.internalDecodeSpeedToUH(i2);
//        stopAlarm = Utils.internalDecodeSpeedToUH(i3);
//        infusionUnit = Utils.internalDecodeSpeedToUH(i4);
//        basalAlarm = Utils.internalDecodeSpeedToUH(i5);
//        largeAlarm = Utils.internalDecodeSpeedToUH(i6);
//        aapsLogger.error(LTag.EQUILBLE,
//                "CmdSettingSet===" + Crc.bytesToHex(data) + "====" + lowAlarm);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }


}
