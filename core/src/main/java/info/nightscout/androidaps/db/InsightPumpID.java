package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "InsightPumpIDs")
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
