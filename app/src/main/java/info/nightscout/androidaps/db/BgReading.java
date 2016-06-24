package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.client.data.NSCal;
import info.nightscout.client.data.NSSgv;

@DatabaseTable(tableName = "BgReadings")
public class BgReading implements DataPointInterface {
    private static Logger log = LoggerFactory.getLogger(BgReading.class);
    public static final DecimalFormat mmolFormat = new DecimalFormat("0.0");
    public static final DecimalFormat mgdlFormat = new DecimalFormat("0");

    public long getTimeIndex() {
        return timeIndex;
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;

    @DatabaseField
    public double value;

    @DatabaseField
    public double slope;

    @DatabaseField
    public double raw;

    @DatabaseField
    public int battery_level;

    public static String units = Constants.MGDL;

    public BgReading() {}

    public BgReading(NSSgv sgv) {
        timeIndex = sgv.getMills();
        value = sgv.getMgdl();
        raw = sgv.getFiltered();
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
        return "BgReading{" +
                "timeIndex=" + timeIndex +
                ", date=" + new Date(timeIndex) +
                ", value=" + value +
                ", slope=" + slope +
                ", raw=" + raw +
                ", battery_level=" + battery_level +
                '}';
    }

    @Override
    public double getX() {
        return timeIndex;
    }

    @Override
    public double getY() {
        return valueToUnits(units);
    }
}
