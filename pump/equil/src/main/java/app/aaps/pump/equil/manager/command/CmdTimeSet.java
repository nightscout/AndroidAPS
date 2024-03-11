package app.aaps.pump.equil.manager.command;


import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;

import java.util.Calendar;

public class CmdTimeSet extends BaseSetting {
    public CmdTimeSet() {
        super(System.currentTimeMillis());
        port = "0505";
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x01, 0x00};
        byte[] data3 = new byte[6];
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 2000;
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        data3[0] = (byte) year;
        data3[1] = (byte) month;
        data3[2] = (byte) day;
        data3[3] = (byte) hour;
        data3[4] = (byte) minute;
        data3[5] = (byte) second;
        byte[] data = Utils.concat(indexByte, data2, data3);
//        ZLog.e2("setTime==" + year + "==" + month + "===" + day + "===" + hour + "===" + minute + "===" + second);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x00, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return EquilHistoryRecord.EventType.SET_TIME;
    }
}
