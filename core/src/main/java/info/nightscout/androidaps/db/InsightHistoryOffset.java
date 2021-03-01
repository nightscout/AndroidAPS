package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;

@DatabaseTable(tableName = "InsightHistoryOffsets")
public class InsightHistoryOffset {

    @DatabaseField(id = true, canBeNull = false)
    public String pumpSerial;

    @DatabaseField
    public long offset;
}
