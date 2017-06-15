package info.nightscout.androidaps.db;

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TREATMENTS)
public class Treatment implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(Treatment.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField(index = true)
    public long pumpId = 0;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id;

    @DatabaseField
    public double insulin = 0d;
    @DatabaseField
    public double carbs = 0d;
    @DatabaseField
    public boolean mealBolus = true; // true for meal bolus , false for correction bolus

    @DatabaseField
    public int insulinInterfaceID = InsulinInterface.FASTACTINGINSULIN;
    @DatabaseField
    public double dia = Constants.defaultDIA;

    public Treatment() {
    }

    public Treatment(long date) {
        this.date = date;
    }

    public Treatment(InsulinInterface insulin) {
        insulinInterfaceID = insulin.getId();
        dia = insulin.getDia();
    }

    public Treatment(InsulinInterface insulin, double dia) {
        insulinInterfaceID = insulin.getId();
        this.dia = dia;
    }

    public long getMillisecondsFromStart() {
        return System.currentTimeMillis() - date;
    }

    public String toString() {
        return "Treatment{" +
                "date= " + date +
                ", date= " + DateUtil.dateAndTimeString(date) +
                ", isValid= " + isValid +
                ", _id= " + _id +
                ", pumpId= " + pumpId +
                ", insulin= " + insulin +
                ", carbs= " + carbs +
                ", mealBolus= " + mealBolus +
                "}";
    }

    public boolean isDataChanging(Treatment other) {
        if (date != other.date) {
            return true;
        }
        if (insulin != other.insulin)
            return true;
        if (carbs != other.carbs)
            return true;
        return false;
    }

    public boolean isEqual(Treatment other) {
        if (date != other.date) {
            return false;
        }
        if (insulin != other.insulin)
            return false;
        if (carbs != other.carbs)
            return false;
        if (mealBolus != other.mealBolus)
            return false;
        if (pumpId != other.pumpId)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public void copyFrom(Treatment t) {
        date = t.date;
        _id = t._id;
        insulin = t.insulin;
        carbs = t.carbs;
        mealBolus = t.mealBolus;
        pumpId = t.pumpId;
    }

    //  ----------------- DataPointInterface --------------------
    @Override
    public double getX() {
        return date;
    }

    // default when no sgv around available
    private double yValue = 0;

    @Override
    public double getY() {
        return yValue;
    }

    @Override
    public String getLabel() {
        String label = "";
        if (insulin > 0) label += DecimalFormatter.to2Decimal(insulin) + "U";
        if (carbs > 0)
            label += "~" + DecimalFormatter.to0Decimal(carbs) + "g";
        return label;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        return PointsWithLabelGraphSeries.Shape.BOLUS;
    }

    @Override
    public float getSize() {
        return 10;
    }

    @Override
    public int getColor() {
        return Color.CYAN;
    }

    @Override
    public void setY(double y) {
        yValue = y;
    }

    //  ----------------- DataPointInterface end --------------------

    public Iob iobCalc(long time, double dia) {
        InsulinInterface insulinInterface = MainApp.getInsulinIterfaceById(insulinInterfaceID);
        if (insulinInterface == null)
            insulinInterface = ConfigBuilderPlugin.getActiveInsulin();

        return insulinInterface.iobCalcForTreatment(this, time, dia);
    }
}
