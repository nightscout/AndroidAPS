package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

@DatabaseTable(tableName = "History")
public class HistoryRecord {

    @DatabaseField(useGetSet = true)
    private String _id;

    @DatabaseField(useGetSet = true)
    private byte recordCode;

    @DatabaseField(id = true, useGetSet = true)
    private String bytes;

    @DatabaseField(useGetSet = true)
    private long recordDate;

    @DatabaseField(useGetSet = true)
    private double recordValue;

    @DatabaseField(useGetSet = true)
    private String bolusType;

    @DatabaseField(useGetSet = true)
    private String stringRecordValue;

    @DatabaseField(useGetSet = true)
    private int recordDuration;

    @DatabaseField(useGetSet = true)
    private double recordDailyBasal;

    @DatabaseField(useGetSet = true)
    private double recordDailyBolus;

    @DatabaseField(useGetSet = true)
    private String recordAlarm;

    public HistoryRecord() {
        this.recordDate = 0;
        this.recordValue = 0.0;
        this.bolusType = "None";
        this.recordCode = 0x0F;
        this.bytes = new String();
        this._id = null;
    }

    public void setRecordDate(Date dtRecordDate) {
        this.recordDate = dtRecordDate.getTime();
    }

    public long getRecordDate() {
        return this.recordDate;
    }

    public void setRecordDate(long dtRecordDate) {
        this.recordDate = dtRecordDate;
    }

    public double getRecordValue() {
        return this.recordValue;
    }

    public void setRecordValue(double dRecordValue) {
        this.recordValue = dRecordValue;
    }

    public String getBolusType() {
        return this.bolusType;
    }

    public void setBolusType(String strRecordType) {
        this.bolusType = strRecordType;
    }

    public String getStringRecordValue() {
        return this.stringRecordValue;
    }

    public void setStringRecordValue(String strRecordValue) {
        this.stringRecordValue = strRecordValue;
    }

    public byte getRecordCode() {
        return this.recordCode;
    }

    public void setRecordCode(byte cRecordCode) {
        this.recordCode = cRecordCode;
    }

    public int getRecordDuration() {
        return this.recordDuration;
    }

    public void setRecordDuration(int dRecordDuraion) {
        this.recordDuration = dRecordDuraion;
    }

    public double getRecordDailyBasal() {
        return this.recordDailyBasal;
    }

    public void setRecordDailyBasal(double dRecordDailyBasal) {
        this.recordDailyBasal = dRecordDailyBasal;
    }

    public double getRecordDailyBolus() {
        return this.recordDailyBolus;
    }

    public void setRecordDailyBolus(double dRecordDailyBolus) {
        this.recordDailyBolus = dRecordDailyBolus;
    }

    public int getRecordLevel(double dExLow, double dLow, double dHigh, double dExHigh) {
        if (this.recordValue < dExLow)
            return 0;
        if (this.recordValue < dLow)
            return 1;
        if (this.recordValue < dHigh)
            return 2;
        return this.recordValue < dExHigh ? 3 : 4;
    }

    public String getRecordAlarm() {
        return this.recordAlarm;
    }

    public void setRecordAlarm(String strAlarm) {
        this.recordAlarm = strAlarm;
    }

    public String get_id() {
        return this._id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public void setBytes(byte[] raw) {
        this.bytes = bytesToHex(raw);
    }

    public void setBytes(String bytes) {
        this.bytes = bytes;
    }

    public String getBytes() {
        return this.bytes;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

