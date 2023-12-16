package app.aaps.pump.equil.manager.command;

import androidx.annotation.NonNull;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdHistoryGet extends BaseSetting {

    private int battery;
    private int medicine;
    private int rate;
    private int largeRate;
    private int year;
    private int month;
    private int day;
    private int hour;
    private int min;
    private int second;
    private int index;
    //    private int port;
    private int type;
    private int level;
    private int parm;
    private int currentIndex = 0;
    private int resultIndex;

    public CmdHistoryGet() {
        super(System.currentTimeMillis());
        this.port = "0505";
    }

    public CmdHistoryGet(int currentIndex) {
        this();
        this.currentIndex = currentIndex;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x01};
        byte[] data3 = Utils.intToBytes(currentIndex);
        byte[] data = Utils.concat(indexByte, data2, data3);
        pumpReqIndex++;
        aapsLogger.debug(LTag.PUMPCOMM, "getReqData2===" + Utils.bytesToHex(data));
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x01, 0x01};
        aapsLogger.debug(LTag.PUMPCOMM, "currentIndex===" + currentIndex);
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
//        67631679050017070e101319
        int index1 = data[4];
        year = data[6] & 0xff;
        month = data[7] & 0xff;
        day = data[8] & 0xff;
        hour = data[9] & 0xff;
        min = data[10] & 0xff;
        second = data[11] & 0xff;
        //a5e207590501 17070e100f161100000000007d0204080000
        //ae6ae9100501 17070e100f16 1100000000007d0204080000
        battery = data[12] & 0xff;
        medicine = data[13] & 0xff;
        rate = Utils.bytesToInt(data[15], data[14]);
        largeRate = Utils.bytesToInt(data[17], data[16]);
        index = Utils.bytesToInt(data[19], data[18]);
//        port = data[20] & 0xff;
        type = data[21] & 0xff;
        level = data[22] & 0xff;
        parm = data[23] & 0xff;
        if (currentIndex != 0) {
            equilManager.decodeHistory(data);
        }
        resultIndex = index;
        aapsLogger.debug(LTag.PUMPCOMM, "history index==" + index + "===" + Utils.bytesToHex(data) +
                "===" + rate + "====" + largeRate + "===" + Utils.bytesToHex(new byte[]{data[16],
                data[17]}));
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    @NonNull @Override
    public String toString() {
        return "CmdHistoryGet{" +
                "battery=" + battery +
                ", medicine=" + medicine +
                ", rate=" + rate +
                ", largeRate=" + largeRate +
                ", year=" + year +
                ", month=" + month +
                ", day=" + day +
                ", hour=" + hour +
                ", min=" + min +
                ", second=" + second +
                ", index=" + index +
                ", port=" + port +
                ", type=" + type +
                ", level=" + level +
                ", parm=" + parm +
                '}';
    }

    public int getCurrentIndex() {
        return resultIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
