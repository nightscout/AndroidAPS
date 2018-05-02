package info.nightscout.androidaps.db;

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSMbg;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Translator;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_CAREPORTALEVENTS)
public class CareportalEvent implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(CareportalEvent.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id;

    @DatabaseField
    public String eventType;
    @DatabaseField
    public String json;

    public static final String CARBCORRECTION = "Carb Correction";
    public static final String BOLUSWIZARD = "Bolus Wizard";
    public static final String CORRECTIONBOLUS = "Correction Bolus";
    public static final String MEALBOLUS = "Meal Bolus";
    public static final String COMBOBOLUS = "Combo Bolus";
    public static final String TEMPBASAL = "Temp Basal";
    public static final String TEMPORARYTARGET = "Temporary Target";
    public static final String PROFILESWITCH = "Profile Switch";
    public static final String SITECHANGE = "Site Change";
    public static final String INSULINCHANGE = "Insulin Change";
    public static final String SENSORCHANGE = "Sensor Change";
    public static final String PUMPBATTERYCHANGE = "Pump Battery Change";
    public static final String BGCHECK = "BG Check";
    public static final String ANNOUNCEMENT = "Announcement";
    public static final String NOTE = "Note";
    public static final String QUESTION = "Question";
    public static final String EXERCISE = "Exercise";
    public static final String OPENAPSOFFLINE = "OpenAPS Offline";
    public static final String NONE = "<none>";

    public static final String MBG = "Mbg"; // comming from entries

    public CareportalEvent() {
    }

    public CareportalEvent(NSMbg mbg) {
        date = mbg.date;
        eventType = MBG;
        json = mbg.json;
    }

    public long getMillisecondsFromStart() {
        return System.currentTimeMillis() - date;
    }

    public long getHoursFromStart() {
        return (System.currentTimeMillis() - date) / (60 * 1000);
    }

    public String age() {
        Map<TimeUnit, Long> diff = computeDiff(date, System.currentTimeMillis());
        if (OverviewFragment.shorttextmode)
            return diff.get(TimeUnit.DAYS) +"d" + diff.get(TimeUnit.HOURS) + "h";
        else
            return diff.get(TimeUnit.DAYS) + " " + MainApp.gs(R.string.days) + " " + diff.get(TimeUnit.HOURS) + " " + MainApp.gs(R.string.hours);
    }

    public boolean isOlderThan(double hours) {
        Map<TimeUnit, Long> diff = computeDiff(date, System.currentTimeMillis());
            if(diff.get(TimeUnit.DAYS)*24 + diff.get(TimeUnit.HOURS) > hours)
                return true;
            else
                return false;
    }

    public String log() {
        return "CareportalEvent{" +
                "date= " + date +
                ", date= " + DateUtil.dateAndTimeString(date) +
                ", isValid= " + isValid +
                ", _id= " + _id +
                ", eventType= " + eventType +
                ", json= " + json +
                "}";
    }

    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    public static Map<TimeUnit, Long> computeDiff(long date1, long date2) {
        long diffInMillies = date2 - date1;
        List<TimeUnit> units = new ArrayList<TimeUnit>(EnumSet.allOf(TimeUnit.class));
        Collections.reverse(units);
        Map<TimeUnit, Long> result = new LinkedHashMap<TimeUnit, Long>();
        long milliesRest = diffInMillies;
        for (TimeUnit unit : units) {
            long diff = unit.convert(milliesRest, TimeUnit.MILLISECONDS);
            long diffInMilliesForUnit = unit.toMillis(diff);
            milliesRest = milliesRest - diffInMilliesForUnit;
            result.put(unit, diff);
        }
        return result;
    }

    // -------- DataPointWithLabelInterface -------

    @Override
    public double getX() {
        return date;
    }

    double yValue = 0;

    @Override
    public double getY() {
        String units = MainApp.getConfigBuilder().getProfileUnits();
        if (eventType.equals(MBG)) {
            double mbg = 0d;
            try {
                JSONObject object = new JSONObject(json);
                mbg = object.getDouble("mgdl");
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
            return Profile.fromMgdlToUnits(mbg, units);
        }

        double glucose = 0d;
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("glucose")) {
                glucose = object.getDouble("glucose");
                units = object.getString("units");
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        if (glucose != 0d) {
            double mmol = 0d;
            double mgdl = 0;
            if (units.equals(Constants.MGDL)) {
                mgdl = glucose;
                mmol = glucose * Constants.MGDL_TO_MMOLL;
            }
            if (units.equals(Constants.MMOL)) {
                mmol = glucose;
                mgdl = glucose * Constants.MMOLL_TO_MGDL;
            }
            return Profile.toUnits(mgdl, mmol, units);
        }

        return yValue;
    }

    @Override
    public void setY(double y) {
        yValue = y;
    }

    @Override
    public String getLabel() {
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("notes"))
                return StringUtils.abbreviate(object.getString("notes"), 40);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return Translator.translate(eventType);
    }

    public String getNotes() {
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("notes"))
                return object.getString("notes");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return "";
    }

    @Override
    public long getDuration() {
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("duration"))
                return object.getInt("duration") * 60 * 1000L;
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        switch (eventType) {
            case CareportalEvent.MBG:
                return PointsWithLabelGraphSeries.Shape.MBG;
            case CareportalEvent.BGCHECK:
                return PointsWithLabelGraphSeries.Shape.BGCHECK;
            case CareportalEvent.ANNOUNCEMENT:
                return PointsWithLabelGraphSeries.Shape.ANNOUNCEMENT;
            case CareportalEvent.OPENAPSOFFLINE:
                return PointsWithLabelGraphSeries.Shape.OPENAPSOFFLINE;
            case CareportalEvent.EXERCISE:
                return PointsWithLabelGraphSeries.Shape.EXERCISE;
        }
        if (getDuration() > 0)
            return PointsWithLabelGraphSeries.Shape.GENERALWITHDURATION;
        return PointsWithLabelGraphSeries.Shape.GENERAL;
    }

    @Override
    public float getSize() {
        boolean isTablet = MainApp.sResources.getBoolean(R.bool.isTablet);
        return isTablet ? 12 : 10;
    }

    @Override
    public int getColor() {
        if (eventType.equals(ANNOUNCEMENT))
            return MainApp.sResources.getColor(R.color.notificationAnnouncement);
        if (eventType.equals(MBG))
            return Color.RED;
        if (eventType.equals(BGCHECK))
            return Color.RED;
        if (eventType.equals(EXERCISE))
            return Color.BLUE;
        if (eventType.equals(OPENAPSOFFLINE))
            return Color.GRAY;
        return Color.GRAY;
    }

}
