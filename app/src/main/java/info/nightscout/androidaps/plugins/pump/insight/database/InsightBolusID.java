package info.nightscout.androidaps.plugins.pump.insight.database;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import info.nightscout.androidaps.db.DatabaseHelper;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_INSIGHT_BOLUS_IDS)
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
