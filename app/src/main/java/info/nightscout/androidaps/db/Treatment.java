package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
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
    public int insulinType = InsulinInterface.FASTACTINGINSULIN;

    @DatabaseField
    public double dia = Constants.defaultDIA;

    @DatabaseField
    public Double carbs = 0d;

    @DatabaseField
    public boolean mealBolus = true; // true for meal bolus , false for correction bolus

    public Treatment() {
        InsulinInterface insulin = MainApp.getConfigBuilder().getActiveInsulin();
        if (insulin != null) {
            insulinType = insulin.getId();
            dia = insulin.getDia();
        } else {
            insulinType = InsulinInterface.FASTACTINGINSULIN;
            dia = Constants.defaultDIA;
        }
    }

    public Treatment(InsulinInterface insulin) {
        insulinType = insulin.getId();
        dia = insulin.getDia();
    }

    public void copyFrom(Treatment t) {
        this._id = t._id;
        this.created_at = t.created_at;
        this.insulin = t.insulin;
        this.carbs = t.carbs;
        this.mealBolus = t.mealBolus;
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
