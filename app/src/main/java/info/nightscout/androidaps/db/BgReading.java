package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SP;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_BGREADINGS)
public class BgReading implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(BgReading.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public double value;
    @DatabaseField
    public String direction;
    @DatabaseField
    public double raw;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id = null; // NS _id


    public static String units = Constants.MGDL;

    public boolean isPrediction = false; // true when drawing predictions as bg points

    public BgReading() {}

    public BgReading(NSSgv sgv) {
        date = sgv.getMills();
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
                "date=" + date +
                ", date=" + DateUtil.dateAndTimeString(date) +
                ", value=" + value +
                ", direction=" + direction +
                ", raw=" + raw +
                '}';
    }

    // ------------------ DataPointWithLabelInterface ------------------
    @Override
    public double getX() {
        return date;
    }

    @Override
    public double getY() {
        return valueToUnits(units);
    }

    @Override
    public void setY(double y) {

    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        return PointsWithLabelGraphSeries.Shape.POINT;
    }

    @Override
    public float getSize() {
        boolean isTablet = MainApp.sResources.getBoolean(R.bool.isTablet);
        return isTablet ? 8 : 5;
    }

    @Override
    public int getColor() {
        Double lowLine = SP.getDouble("low_mark", 0d);
        Double highLine = SP.getDouble("high_mark", 0d);
        if (lowLine < 1) {
            lowLine = Profile.fromMgdlToUnits(OverviewPlugin.bgTargetLow, units);
        }
        if (highLine < 1) {
            highLine = Profile.fromMgdlToUnits(OverviewPlugin.bgTargetHigh, units);
        }
        String units = MainApp.getConfigBuilder().getProfile().getUnits();
        int color = MainApp.sResources.getColor(R.color.inrange);
        if (isPrediction)
            color = MainApp.sResources.getColor(R.color.prediction);
        else if (valueToUnits(units) < lowLine)
            color = MainApp.sResources.getColor(R.color.low);
        else if (valueToUnits(units) > highLine)
            color = MainApp.sResources.getColor(R.color.high);
        return color;
    }

}
