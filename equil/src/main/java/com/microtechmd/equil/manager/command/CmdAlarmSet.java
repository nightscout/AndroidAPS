package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.data.AlarmMode;
import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.manager.Utils;

public class CmdAlarmSet extends BaseSetting {

    public CmdAlarmSet(int mode) {
        super(System.currentTimeMillis());
        this.mode = mode;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x0b};
        byte[] data3 = Utils.intToBytes(mode);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x0b, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }


    int mode;

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        if (mode == AlarmMode.MUTE.getCommand()) {
            return EquilHistoryRecord.EventType.SET_ALARM_MUTE;
        } else if (mode == AlarmMode.TONE.getCommand()) {
            return EquilHistoryRecord.EventType.SET_ALARM_TONE;
        } else if (mode == AlarmMode.TONE_AND_SHAKE.getCommand()) {
            return EquilHistoryRecord.EventType.SET_ALARM_TONE_AND_SHAK;
        } else if (mode == AlarmMode.SHAKE.getCommand()) {
            return EquilHistoryRecord.EventType.SET_ALARM_SHAKE;
        }
        return null;
    }
}
