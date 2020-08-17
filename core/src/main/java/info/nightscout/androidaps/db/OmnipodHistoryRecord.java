package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;

/**
 * Created by andy on 30.11.2019.
 */
@DatabaseTable(tableName = "PodHistory")
public class OmnipodHistoryRecord implements DbObjectBase, Comparable<OmnipodHistoryRecord> {

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    private long podEntryTypeCode;

    @DatabaseField
    private String data;

    @DatabaseField
    private boolean success;

    @DatabaseField
    private long pumpId;

    @DatabaseField
    private String podSerial;

    @DatabaseField
    private Boolean successConfirmed;

    public OmnipodHistoryRecord() {
        generatePumpId();
    }

    public OmnipodHistoryRecord(long dateTimeInMillis, long podEntryTypeCode) {
        this.date = dateTimeInMillis;
        this.podEntryTypeCode = podEntryTypeCode;
        generatePumpId();
    }

    @Override
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

    @Override
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
    public int compareTo(OmnipodHistoryRecord otherOne) {
        return (int) (otherOne.date - this.date);
    }
}
