package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.EquilConst;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdSettingSet extends BaseSetting {
    double lowAlarm;

    int bolusThresholdStep = EquilConst.EQUIL_BLOUS_THRESHOLD_STEP;

    public CmdSettingSet() {
        super(System.currentTimeMillis());
    }

    public CmdSettingSet(double maxBolus) {
        super(System.currentTimeMillis());
        bolusThresholdStep = Utils.decodeSpeedToUH(maxBolus);
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] equilCmd = new byte[]{0x01, 0x05};
        byte[] useTime = Utils.intToBytes(0);
        byte[] autoCloseTime = Utils.intToBytes(0);
        byte[] lowAlarmByte = Utils.intToTwoBytes(bolusThresholdStep);
        byte[] fastBolus = Utils.intToTwoBytes(0);
        byte[] occlusion = Utils.intToTwoBytes(2800);
        byte[] insulinUnit = Utils.intToTwoBytes(8);
        byte[] basalThreshold = Utils.intToTwoBytes(240);
        byte[] bolusThreshold = Utils.intToTwoBytes(1600);
        byte[] data = Utils.concat(indexByte, equilCmd, useTime, autoCloseTime,
                lowAlarmByte, fastBolus,
                occlusion, insulinUnit, basalThreshold, bolusThreshold);
        pumpReqIndex++;
        aapsLogger.debug(LTag.PUMPCOMM,
                "CmdSettingSet data===" + Utils.bytesToHex(data) + "====" + lowAlarm + "===" + Utils.decodeSpeedToUH(lowAlarm));
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x05, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
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
//        aapsLogger.debug(LTag.PUMPCOMM,
//                "CmdSettingSet===" + Crc.bytesToHex(data) + "====" + lowAlarm);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    public boolean isPairStep() {
        return true;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
