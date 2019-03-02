package info.nightscout.androidaps.plugins.pump.insight.database;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import info.nightscout.androidaps.db.DatabaseHelper;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_INSIGHT_HISTORY_OFFSETS)
public class InsightHistoryOffset {

    @DatabaseField(id = true, canBeNull = false)
    public String pumpSerial;

    @DatabaseField
    public long offset;
}
