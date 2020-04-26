package info.nightscout.androidaps.plugins.pump.insight.database;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import info.nightscout.androidaps.db.DatabaseHelper;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_INSIGHT_PUMP_IDS)
public class InsightPumpID {

    @DatabaseField(generatedId = true)
    public Long id;

    @DatabaseField
    public long timestamp;

    @DatabaseField
    public String eventType;

    @DatabaseField
    public String pumpSerial;

    @DatabaseField
    public long eventID;

}
