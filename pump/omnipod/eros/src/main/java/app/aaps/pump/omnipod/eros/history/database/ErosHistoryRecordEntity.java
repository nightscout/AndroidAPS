package app.aaps.pump.omnipod.eros.history.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import app.aaps.core.utils.DateTimeUtil;

/**
 * Created by andy on 30.11.2019.
 */
@Entity(tableName = "historyrecords", indices = {@Index("date")})
public class ErosHistoryRecordEntity implements Comparable<ErosHistoryRecordEntity> {

    @PrimaryKey
    private long pumpId;
    public long date;
    private long podEntryTypeCode;
    @Nullable private String data = null;
    private boolean success = false;
    private String podSerial = "None";
    private Boolean successConfirmed = false;

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

    @Nullable public String getData() {
        return data;
    }

    public void setData(@Nullable String data) {
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
    public int compareTo(@NonNull ErosHistoryRecordEntity otherOne) {
        return (int) (otherOne.date - this.date);
    }
}
