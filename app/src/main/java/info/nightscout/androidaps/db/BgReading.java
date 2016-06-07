package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.client.data.NSCal;
import info.nightscout.client.data.NSSgv;

@DatabaseTable(tableName = "BgReadings")
public class BgReading {
    private static Logger log = LoggerFactory.getLogger(BgReading.class);
    public static final DecimalFormat mmolFormat = new DecimalFormat("0.0");
    public static final DecimalFormat mgdlFormat = new DecimalFormat("0");

    public long getTimeIndex() {
        return (long) Math.ceil(timestamp / 60000d);
    }

    public void setTimeIndex(long timeIndex) {
        this.timestamp = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;

    @DatabaseField
    public long timestamp;

    @DatabaseField
    public double value;

    @DatabaseField
    public double slope;

    @DatabaseField
    public double raw;

    @DatabaseField
    public int battery_level;

    public BgReading() {}

    public BgReading(NSSgv sgv) {
        timestamp = sgv.getMills();
        value = sgv.getMgdl();
        raw = sgv.getFiltered();
    }

    public String valInUnit(String units) {
        DecimalFormat formatMmol = new DecimalFormat("0.0");
        DecimalFormat formatMgdl = new DecimalFormat("0");
        if (units.equals(Constants.MGDL)) return formatMgdl.format(value);
        else return formatMmol.format(value/18d);
    }

    public Double valueToUnits(String units) {
        if (units.equals(Constants.MGDL))
            return value;
        else
            return value * Constants.MGDL_TO_MMOLL;
    }

    public String valueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return mgdlFormat.format(value);
        else return mmolFormat.format(value * Constants.MGDL_TO_MMOLL);
    }

    @Override
    public String toString() {
        return "TempBasal{" +
                "timeIndex=" + timeIndex +
                ", timestamp=" + timestamp +
                ", date=" + new Date(timestamp) +
                ", value=" + value +
                ", slope=" + slope +
                ", raw=" + raw +
                ", battery_level=" + battery_level +
                '}';
    }
}
