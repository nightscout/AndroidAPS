package info.nightscout.androidaps.plugins.Treatments;

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.JsonHelper;

@DatabaseTable(tableName = Treatment.TABLE_TREATMENTS)
public class Treatment implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(Treatment.class);

    public static final String TABLE_TREATMENTS = "Treatments";

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
    public boolean isSMB = false;

    @DatabaseField
    public int insulinInterfaceID = InsulinInterface.FASTACTINGINSULIN; // currently unused, will be used in the future
    @DatabaseField
    public double dia = Constants.defaultDIA; // currently unused, will be used in the future

    public Treatment() {
    }

    public static Treatment createFromJson(JSONObject json) throws JSONException {
        Treatment treatment = new Treatment();
        treatment.source = Source.NIGHTSCOUT;
        treatment.date = DateUtil.roundDateToSec(JsonHelper.safeGetLong(json, "mills"));
        if (treatment.date == 0L)
            return null;
        treatment.carbs = JsonHelper.safeGetDouble(json,"carbs");
        treatment.insulin = JsonHelper.safeGetDouble(json,"insulin");
        treatment.pumpId = JsonHelper.safeGetLong(json, "pumpId");
        treatment._id = json.getString("_id");
        treatment.isSMB = JsonHelper.safeGetBoolean(json,"isSMB");
        if (json.has("eventType")) {
            treatment.mealBolus = !json.get("eventType").equals("Correction Bolus");
            double carbs = treatment.carbs;
            if (json.has("boluscalc")) {
                JSONObject boluscalc = json.getJSONObject("boluscalc");
                if (boluscalc.has("carbs")) {
                    carbs = Math.max(boluscalc.getDouble("carbs"), carbs);
                }
            }
            if (carbs <= 0)
                treatment.mealBolus = false;
        }
        return treatment;
    }

    public String toString() {
        return "Treatment{" +
                "date= " + date +
                ", date= " + DateUtil.dateAndTimeString(date) +
                ", isValid= " + isValid +
                ", isSMB= " + isSMB +
                ", _id= " + _id +
                ", pumpId= " + pumpId +
                ", insulin= " + insulin +
                ", carbs= " + carbs +
                ", mealBolus= " + mealBolus +
                "}";
    }

    public boolean isDataChanging(Treatment other) {
        if (date != other.date)
            return true;
        if (insulin != other.insulin)
            return true;
        if (carbs != other.carbs)
            return true;
        return false;
    }

    public boolean isEqual(Treatment other) {
        if (date != other.date)
            return false;
        if (insulin != other.insulin)
            return false;
        if (carbs != other.carbs)
            return false;
        if (mealBolus != other.mealBolus)
            return false;
        if (pumpId != other.pumpId)
            return false;
        if (isSMB != other.isSMB)
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
        isSMB = t.isSMB;
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
        return isSMB ? OverviewPlugin.getPlugin().determineLowLine() : yValue;
    }

    @Override
    public String getLabel() {
        String label = "";
        if (insulin > 0) label += DecimalFormatter.toPumpSupportedBolus(insulin) + "U";
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
        if (isSMB)
            return PointsWithLabelGraphSeries.Shape.SMB;
        else
            return PointsWithLabelGraphSeries.Shape.BOLUS;
    }

    @Override
    public float getSize() {
        return 2;
    }

    @Override
    public int getColor() {
        if (isSMB)
            return MainApp.sResources.getColor(R.color.tempbasal);
        else if (isValid)
            return Color.CYAN;
        else
            return MainApp.instance().getResources().getColor(android.R.color.holo_red_light);
    }

    @Override
    public void setY(double y) {
        yValue = y;
    }

    //  ----------------- DataPointInterface end --------------------

    public Iob iobCalc(long time, double dia) {
        if (!isValid)
            return new Iob();

        InsulinInterface insulinInterface = ConfigBuilderPlugin.getActiveInsulin();
        return insulinInterface.iobCalcForTreatment(this, time, dia);
    }
}
