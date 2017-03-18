package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import com.jjoe64.graphview.series.DataPointInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_BGREADINGS)
public class BgReading implements DataPointInterface {
    private static Logger log = LoggerFactory.getLogger(BgReading.class);

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
    public String direction;

    @DatabaseField
    public double raw;

    @DatabaseField
    public int battery_level;

    public static String units = Constants.MGDL;

    public BgReading() {}

    public BgReading(NSSgv sgv) {
        timeIndex = sgv.getMills();
        value = sgv.getMgdl();
        raw = sgv.getFiltered() != null ? sgv.getFiltered() : value;
        direction = sgv.getDirection();
    }

    public Double valueToUnits(String units) {
        if (units.equals(Constants.MGDL))
            return value;
        else
            return value * Constants.MGDL_TO_MMOLL;
    }

    public String valueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(value);
        else return DecimalFormatter.to1Decimal(value * Constants.MGDL_TO_MMOLL);
    }

     public String directionToSymbol() {
        String symbol = "";
        if (direction.compareTo("DoubleDown") == 0) {
            symbol = "\u21ca";
        } else if (direction.compareTo("SingleDown") == 0) {
            symbol = "\u2193";
        } else if (direction.compareTo("FortyFiveDown") == 0) {
            symbol = "\u2198";
        } else if (direction.compareTo("Flat") == 0) {
            symbol = "\u2192";
        } else if (direction.compareTo("FortyFiveUp") == 0) {
            symbol = "\u2197";
        } else if (direction.compareTo("SingleUp") == 0) {
            symbol = "\u2191";
        } else if (direction.compareTo("DoubleUp") == 0) {
            symbol = "\u21c8";
        } else if (isSlopeNameInvalid(direction)) {
            symbol = "??";
        }
        return symbol;
    }

    public static boolean isSlopeNameInvalid(String direction) {
        if (direction.compareTo("NOT_COMPUTABLE") == 0 ||
                direction.compareTo("NOT COMPUTABLE") == 0 ||
                direction.compareTo("OUT_OF_RANGE") == 0 ||
                direction.compareTo("OUT OF RANGE") == 0 ||
                direction.compareTo("NONE") == 0) {
            return true;
        } else {
            return false;
        }
    }


    @Override
    public String toString() {
        return "BgReading{" +
                "timeIndex=" + timeIndex +
                ", date=" + new Date(timeIndex) +
                ", value=" + value +
                ", direction=" + direction +
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
