package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdSettingGet extends BaseSetting {
    private long useTime;
    private long closeTime;
    private float lowAlarm;
    private float largefastAlarm;
    private float stopAlarm;
    private float infusionUnit;
    private float basalAlarm;
    private float largeAlarm;

    public CmdSettingGet() {
        super(System.currentTimeMillis());
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x05};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
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


        int i1 = Utils.bytesToInt(data[15], data[14]);
        int i2 = Utils.bytesToInt(data[17], data[16]);
        int i3 = Utils.bytesToInt(data[19], data[18]);
        int i4 = Utils.bytesToInt(data[21], data[20]);
        int i5 = Utils.bytesToInt(data[23], data[22]);
        int i6 = Utils.bytesToInt(data[25], data[24]);
        lowAlarm = Utils.internalDecodeSpeedToUH(i1);
        largefastAlarm = Utils.internalDecodeSpeedToUH(i2);
        stopAlarm = Utils.internalDecodeSpeedToUH(i3);
        infusionUnit = Utils.internalDecodeSpeedToUH(i4);
        basalAlarm = Utils.internalDecodeSpeedToUH(i5);
        largeAlarm = Utils.internalDecodeSpeedToUH(i6);
        aapsLogger.debug(LTag.PUMPCOMM,
                "CmdSettingGet===" + Utils.bytesToHex(data) + "====" + lowAlarm + "=======" + i1);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    public long getUseTime() {
        return useTime;
    }

    public void setUseTime(long useTime) {
        this.useTime = useTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(long closeTime) {
        this.closeTime = closeTime;
    }

    public float getLowAlarm() {
        return lowAlarm;
    }

    public void setLowAlarm(float lowAlarm) {
        this.lowAlarm = lowAlarm;
    }

    public float getLargefastAlarm() {
        return largefastAlarm;
    }

    public void setLargefastAlarm(float largefastAlarm) {
        this.largefastAlarm = largefastAlarm;
    }

    public float getStopAlarm() {
        return stopAlarm;
    }

    public void setStopAlarm(float stopAlarm) {
        this.stopAlarm = stopAlarm;
    }

    public float getInfusionUnit() {
        return infusionUnit;
    }

    public void setInfusionUnit(float infusionUnit) {
        this.infusionUnit = infusionUnit;
    }

    public float getBasalAlarm() {
        return basalAlarm;
    }

    public void setBasalAlarm(float basalAlarm) {
        this.basalAlarm = basalAlarm;
    }

    public float getLargeAlarm() {
        return largeAlarm;
    }

    public void setLargeAlarm(float largeAlarm) {
        this.largeAlarm = largeAlarm;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
