package info.nightscout.androidaps.plugins.treatments;

import android.graphics.Color;
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.DbObjectBase;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.JsonHelper;

@DatabaseTable(tableName = Treatment.TABLE_TREATMENTS)
public class Treatment implements DataPointWithLabelInterface, DbObjectBase {
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
    public int insulinInterfaceID = InsulinInterface.OREF_RAPID_ACTING; // currently unused, will be used in the future
    @DatabaseField
    public double dia = Constants.defaultDIA; // currently unused, will be used in the future
    @DatabaseField
    public String boluscalc;

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
                treatment.boluscalc = boluscalc.toString();
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
                ", date= " + new Date(date).toLocaleString() +
                ", isValid= " + isValid +
                ", isSMB= " + isSMB +
                ", _id= " + _id +
                ", pumpId= " + pumpId +
                ", insulin= " + insulin +
                ", carbs= " + carbs +
                ", mealBolus= " + mealBolus +
                ", source= " + source +
                "}";
    }

    public boolean isDataChanging(Treatment other) {
        if (date != other.date)
            return true;
        if (!isSame(insulin, other.insulin))
            return true;
        if (!isSame(carbs, other.carbs))
            return true;
        return false;
    }

    public boolean isEqual(Treatment other) {
        if (date != other.date)
            return false;
        if (!isSame(insulin, other.insulin))
            return false;
        if (!isSame(carbs, other.carbs))
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

    public boolean isEqualWithoutPumpId(Treatment other) {
        if (date != other.date)
            return false;
        if (!isSame(insulin, other.insulin))
            return false;
        if (!isSame(carbs, other.carbs))
            return false;
        if (mealBolus != other.mealBolus)
            return false;
        if (isSMB != other.isSMB)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }

    @Nullable
    public JSONObject getBoluscalc() {
        try {
            if (boluscalc != null)
                return new JSONObject(boluscalc);
        } catch (JSONException ignored) {
        }
        return null;
    }

    public double getIc() {
        JSONObject bw = getBoluscalc();
         if (bw == null || !bw.has("ic")) {
             Profile profile = ProfileFunctions.getInstance().getProfile(date);
             return profile.getIc(date);
        }
         return JsonHelper.safeGetDouble(bw, "ic");
    }

    /*
     * mealBolus, _id and isSMB cannot be known coming from pump. Only compare rest
     * TODO: remove debug toasts
     */
    public boolean equalsRePumpHistory(Treatment other) {
        if (date != other.date) {
            return false;
        }

        if (!isSame(insulin, other.insulin))
            return false;

        if (!isSame(carbs, other.carbs))
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

    public void copyBasics(Treatment t) {
        date = t.date;
        insulin = t.insulin;
        carbs = t.carbs;
        pumpId = t.pumpId;
        source = t.source;
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
        return isSMB ? OverviewPlugin.INSTANCE.determineLowLine() : yValue;
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
            return MainApp.gc(R.color.tempbasal);
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

        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();
        return insulinInterface.iobCalcForTreatment(this, time, dia);
    }

    @Override
    public long getDate() {
        return this.date;
    }

    @Override
    public long getPumpId() {
        return this.pumpId;
    }
}