package info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import info.nightscout.core.utils.DateTimeUtil;

/**
 * Created by andy on 30.11.2019.
 */
@Entity(tableName = "historyrecords", indices = {@Index("date")})
public class ErosHistoryRecordEntity implements Comparable<ErosHistoryRecordEntity> {

    @PrimaryKey
    private long pumpId;
    public long date;
    private long podEntryTypeCode;
    private String data;
    private boolean success;
    private String podSerial;
    private Boolean successConfirmed;

    public ErosHistoryRecordEntity() {
        generatePumpId();
    }

    public ErosHistoryRecordEntity(long dateTimeInMillis, long podEntryTypeCode) {
        this.date = dateTimeInMillis;
        this.podEntryTypeCode = podEntryTypeCode;
        generatePumpId();
    }


    public long getDate() {
        return this.date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getDateTimeString() {
        return DateTimeUtil.toStringFromTimeInMillis(this.date);
    }

    public long getPodEntryTypeCode() {
        return podEntryTypeCode;
    }

    public void setPodEntryTypeCode(long podEntryTypeCode) {
        this.podEntryTypeCode = podEntryTypeCode;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setPumpId(long pumpId) {
        this.pumpId = pumpId;
    }

    public Boolean getSuccessConfirmed() {
        return successConfirmed;
    }

    public void setSuccessConfirmed(Boolean successConfirmed) {
        this.successConfirmed = successConfirmed;
    }

    public long getPumpId() {
        return pumpId;
    }

    private void generatePumpId() {
        this.pumpId = (DateTimeUtil.toATechDate(this.date) * 100L) + podEntryTypeCode;
    }


    public String getPodSerial() {
        return podSerial;
    }

    public void setPodSerial(String podSerial) {
        this.podSerial = podSerial;
    }

    @Override
    public int compareTo(ErosHistoryRecordEntity otherOne) {
        return (int) (otherOne.date - this.date);
    }
}
