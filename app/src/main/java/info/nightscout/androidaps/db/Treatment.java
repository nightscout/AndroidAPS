package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TREATMENTS)
public class Treatment implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(Treatment.class);

    public long getTimeIndex() {
        return created_at.getTime();
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;

    @DatabaseField
    public String _id;

    @DatabaseField
    public Date created_at;

    @DatabaseField
    public Double insulin = 0d;

    @DatabaseField
    public Double carbs = 0d;

    @DatabaseField
    public boolean mealBolus = true; // true for meal bolus , false for correction bolus

    public void copyFrom(Treatment t) {
        this._id = t._id;
        this.created_at = t.created_at;
        this.insulin = t.insulin;
        this.carbs = t.carbs;
        this.mealBolus = t.mealBolus;
    }

    public Iob iobCalc(Date time, Double dia) {
        Iob result = new Iob();

        Double scaleFactor = 3.0 / dia;
        Double peak = 75d;
        Double end = 180d;

        if (this.insulin != 0d) {
            Long bolusTime = this.created_at.getTime();
            Double minAgo = scaleFactor * (time.getTime() - bolusTime) / 1000d / 60d;

            if (minAgo < peak) {
                Double x1 = minAgo / 5d + 1;
                result.iobContrib = this.insulin * (1 - 0.001852 * x1 * x1 + 0.001852 * x1);
                // units: BG (mg/dL)  = (BG/U) *    U insulin     * scalar
                result.activityContrib = this.insulin * (2 / dia / 60 / peak) * minAgo;

            } else if (minAgo < end) {
                Double x2 = (minAgo - 75) / 5;
                result.iobContrib = this.insulin * (0.001323 * x2 * x2 - 0.054233 * x2 + 0.55556);
                result.activityContrib = this.insulin * (2 / dia / 60 - (minAgo - peak) * 2 / dia / 60 / (60 * 3 - peak));
            }
        }
        return result;
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - created_at.getTime();
    }

    public String log() {
        return "Treatment{" +
                "timeIndex: " + timeIndex +
                ", _id: " + _id +
                ", insulin: " + insulin +
                ", carbs: " + carbs +
                ", mealBolus: " + mealBolus +
                ", created_at: " +
                "}";
    }

    // DataPointInterface
    @Override
    public double getX() {
        return timeIndex;
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
            label += (label.equals("") ? "" : " ") + DecimalFormatter.to0Decimal(carbs) + "g";
        return label;
    }

    public void setYValue(List<BgReading> bgReadingsArray) {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) return;
        for (int r = bgReadingsArray.size() - 1; r >= 0; r--) {
            BgReading reading = bgReadingsArray.get(r);
            if (reading.timeIndex > timeIndex) continue;
            yValue = NSProfile.fromMgdlToUnits(reading.value, profile.getUnits());
            break;
        }
    }

    public void sendToNSClient() {
        JSONObject data = new JSONObject();
        try {
            if (mealBolus)
                data.put("eventType", "Meal Bolus");
            else
                data.put("eventType", "Correction Bolus");
            if (insulin != 0d) data.put("insulin", insulin);
            if (carbs != 0d) data.put("carbs", carbs.intValue());
            data.put("created_at", DateUtil.toISOString(created_at));
            data.put("timeIndex", timeIndex);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        ConfigBuilderPlugin.uploadCareportalEntryToNS(data);
    }

}
