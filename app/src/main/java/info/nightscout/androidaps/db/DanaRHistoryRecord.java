package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_DANARHISTORY)
public class DanaRHistoryRecord {

    @DatabaseField
    public String _id;

    @DatabaseField
    public byte recordCode;

    @DatabaseField(id = true)
    public String bytes;

    @DatabaseField
    public long recordDate;

    @DatabaseField
    public double recordValue;

    @DatabaseField
    public String bolusType;

    @DatabaseField
    public String stringRecordValue;

    @DatabaseField
    public int recordDuration;

    @DatabaseField
    public double recordDailyBasal;

    @DatabaseField
    public double recordDailyBolus;

    @DatabaseField
    public String recordAlarm;

    public DanaRHistoryRecord() {
        this.recordDate = 0;
        this.recordValue = 0.0;
        this.bolusType = "None";
        this.recordCode = 0x0F;
        this.bytes = new String();
        this._id = null;
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

    public void setBytes(byte[] raw) {
        this.bytes = bytesToHex(raw);
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

