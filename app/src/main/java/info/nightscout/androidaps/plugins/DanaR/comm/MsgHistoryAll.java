package info.nightscout.androidaps.plugins.DanaR.comm;

import com.j256.ormlite.dao.Dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.DanaRHistoryRecord;

public class MsgHistoryAll extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryAll.class);

    public MsgHistoryAll() {
        SetCommand(0x41F2);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        byte recordCode = (byte) intFromBuff(bytes, 0, 1);
        Date date = dateFromBuff(bytes, 1);                     // 3 bytes
        Date datetime = dateTimeFromBuff(bytes, 1);             // 5 bytes
        Date datetimewihtsec = dateTimeSecFromBuff(bytes, 1);   // 6 bytes

        byte paramByte5 = (byte) intFromBuff(bytes, 4, 1);
        byte paramByte6 = (byte) intFromBuff(bytes, 5, 1);
        byte paramByte7 = (byte) intFromBuff(bytes, 6, 1);
        byte paramByte8 = (byte) intFromBuff(bytes, 7, 1);
        double value = (double) intFromBuff(bytes, 8, 2);

        DanaRHistoryRecord danaRHistoryRecord = new DanaRHistoryRecord();

        danaRHistoryRecord.setRecordCode(recordCode);
        danaRHistoryRecord.setBytes(bytes);

        switch (recordCode) {
            case RecordTypes.RECORD_TYPE_BOLUS:
                danaRHistoryRecord.setRecordDate(datetime);
                switch (0xF0 & paramByte8) {
                    case 0xA0:
                        danaRHistoryRecord.setBolusType("DS");
                        break;
                    case 0xC0:
                        danaRHistoryRecord.setBolusType("E");
                        break;
                    case 0x80:
                        danaRHistoryRecord.setBolusType("S");
                        break;
                    case 0x90:
                        danaRHistoryRecord.setBolusType("DE");
                        break;
                    default:
                        danaRHistoryRecord.setBolusType("None");
                        break;
                }
                danaRHistoryRecord.setRecordDuration(((int) paramByte8 & 0x0F) * 60 + (int) paramByte7);
                danaRHistoryRecord.setRecordValue(value * 0.01);
                break;
            case RecordTypes.RECORD_TYPE_DAILY:
                danaRHistoryRecord.setRecordDate(date);
                danaRHistoryRecord.setRecordDailyBasal((double) ((int) paramByte5 * 0xFF + (int) paramByte6) * 0.01);
                danaRHistoryRecord.setRecordDailyBolus((double) ((int) paramByte7 * 0xFF + (int) paramByte8) / 0.01);
                break;
            case RecordTypes.RECORD_TYPE_PRIME:
            case RecordTypes.RECORD_TYPE_ERROR:
            case RecordTypes.RECORD_TYPE_REFILL:
            case RecordTypes.RECORD_TYPE_BASALHOUR:
            case RecordTypes.RECORD_TYPE_TB:
                danaRHistoryRecord.setRecordDate(datetimewihtsec);
                danaRHistoryRecord.setRecordValue(value * 0.01);
                break;
            case RecordTypes.RECORD_TYPE_GLUCOSE:
            case RecordTypes.RECORD_TYPE_CARBO:
                danaRHistoryRecord.setRecordDate(datetimewihtsec);
                danaRHistoryRecord.setRecordValue(value);
                break;
            case RecordTypes.RECORD_TYPE_ALARM:
                danaRHistoryRecord.setRecordDate(datetimewihtsec);
                String strAlarm = "None";
                switch ((int) paramByte8) {
                    case 67:
                        strAlarm = "Check";
                        break;
                    case 79:
                        strAlarm = "Occlusion";
                        break;
                    case 66:
                        strAlarm = "Low Battery";
                        break;
                    case 83:
                        strAlarm = "Shutdown";
                        break;
                }
                danaRHistoryRecord.setRecordAlarm(strAlarm);
                danaRHistoryRecord.setRecordValue(value * 0.01);
                break;
            case RecordTypes.RECORD_TYPE_SUSPEND:
                danaRHistoryRecord.setRecordDate(datetimewihtsec);
                String strRecordValue = "Off";
                if ((int) paramByte8 == 79)
                    strRecordValue = "On";
                danaRHistoryRecord.setStringRecordValue(strRecordValue);
                break;
        }

        try {
            Dao<DanaRHistoryRecord, String> daoHistoryRecords = MainApp.getDbHelper().getDaoHistory();
            daoHistoryRecords.createIfNotExists(danaRHistoryRecord);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return;
    }
}
