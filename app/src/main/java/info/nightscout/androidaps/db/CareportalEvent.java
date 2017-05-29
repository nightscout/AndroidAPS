package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

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

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.DateUtil;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_CAREPORTALEVENTS)
public class CareportalEvent {
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

    public CareportalEvent() {
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - date;
    }

    public long getHoursFromStart() {
        return (new Date().getTime() - date) / (60 * 1000);
    }

    public String age() {
        Map<TimeUnit,Long> diff = computeDiff(date, new Date().getTime());
        return diff.get(TimeUnit.DAYS) + " " + MainApp.sResources.getString(R.string.days) + " " + diff.get(TimeUnit.HOURS) + " " + MainApp.sResources.getString(R.string.hours);
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
    public static Map<TimeUnit,Long> computeDiff(long date1, long date2) {
        long diffInMillies = date2 - date1;
        List<TimeUnit> units = new ArrayList<TimeUnit>(EnumSet.allOf(TimeUnit.class));
        Collections.reverse(units);
        Map<TimeUnit,Long> result = new LinkedHashMap<TimeUnit,Long>();
        long milliesRest = diffInMillies;
        for ( TimeUnit unit : units ) {
            long diff = unit.convert(milliesRest,TimeUnit.MILLISECONDS);
            long diffInMilliesForUnit = unit.toMillis(diff);
            milliesRest = milliesRest - diffInMilliesForUnit;
            result.put(unit,diff);
        }
        return result;
    }
}
