package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "InsightBolusIDs")
public class InsightBolusID {

    @DatabaseField(generatedId = true)
    public Long id;

    @DatabaseField
    public String pumpSerial;

    @DatabaseField
    public Long timestamp;

    @DatabaseField
    public Integer bolusID;

    @DatabaseField
    public Long startID;

    @DatabaseField
    public Long endID;

}
