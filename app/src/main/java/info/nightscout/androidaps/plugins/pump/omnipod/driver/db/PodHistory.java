package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.DbObjectBase;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;

/**
 * Created by andy on 30.11.2019.
 */
@DatabaseTable(tableName = DatabaseHelper.DATABASE_POD_HISTORY)
public class PodHistory implements DbObjectBase {

    private static Logger log = LoggerFactory.getLogger(L.DATABASE);

    @DatabaseField(id = true)
    public long date;

    private PodHistoryEntryType podHistoryEntryType;

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



    public PodHistory(PodHistoryEntryType podDbEntryType) {
        this.date = System.currentTimeMillis();
        this.podHistoryEntryType = podDbEntryType;
        this.podEntryTypeCode = podDbEntryType.getCode();
        generatePumpId();
    }


    public PodHistory(long dateTimeInMillis, PodHistoryEntryType podDbEntryType) {
        this.date = dateTimeInMillis;
        this.podHistoryEntryType = podDbEntryType;
        this.podEntryTypeCode = podDbEntryType.getCode();
        generatePumpId();
    }


    @Override
    public long getDate() {
        return this.date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public PodHistoryEntryType getPodDbEntryType() {
        return podHistoryEntryType;
    }

    public void setPodDbEntryType(PodHistoryEntryType podDbEntryType) {
        this.podHistoryEntryType = podDbEntryType;
        this.podEntryTypeCode = podDbEntryType.getCode();
    }

    public long getPodEntryTypeCode() {
        return podEntryTypeCode;
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
}
