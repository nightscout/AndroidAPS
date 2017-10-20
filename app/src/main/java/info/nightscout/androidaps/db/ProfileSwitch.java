package info.nightscout.androidaps.db;

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.ProfileLocal.LocalProfilePlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_PROFILESWITCHES)
public class ProfileSwitch implements Interval, DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(ProfileSwitch.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id = null; // NS _id

    @DatabaseField
    public boolean isCPP = false; // CPP NS="CircadianPercentageProfile"
    @DatabaseField
    public int timeshift = 0; // CPP NS="timeshift"
    @DatabaseField
    public int percentage = 100; // CPP NS="percentage"

    @DatabaseField
    public String profileName = null;

    @DatabaseField
    public String profileJson = null;

    @DatabaseField
    public String profilePlugin = null; // NSProfilePlugin.class.getName();

    @DatabaseField
    public int durationInMinutes = 0;

    private Profile profile = null;

    public Profile getProfileObject() {
        if (profile == null)
            try {
                profile = new Profile(new JSONObject(profileJson), percentage, timeshift);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        return profile;
    }

    public String getCustomizedName() {
        String name = profileName;
        if(LocalProfilePlugin.LOCAL_PROFILE.equals(name)){
            name = DecimalFormatter.to2Decimal(getProfileObject().percentageBasalSum()) + "U ";
        }
        if (isCPP) {
            name += "(" + percentage + "%," + timeshift + "h)";
        }
        return name;
    }

    public boolean isEqual(ProfileSwitch other) {
        if (date != other.date) {
            return false;
        }
        if (durationInMinutes != other.durationInMinutes)
            return false;
        if (percentage != other.percentage)
            return false;
        if (timeshift != other.timeshift)
            return false;
        if (isCPP != other.isCPP)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        if (!Objects.equals(profilePlugin, other.profilePlugin))
            return false;
        if (!Objects.equals(profileJson, other.profileJson))
            return false;
        if (!Objects.equals(profileName, other.profileName))
            return false;
        return true;
    }

    public void copyFrom(ProfileSwitch t) {
        date = t.date;
        _id = t._id;
        durationInMinutes = t.durationInMinutes;
        percentage = t.percentage;
        timeshift = t.timeshift;
        isCPP = t.isCPP;
        profilePlugin = t.profilePlugin;
        profileJson = t.profileJson;
        profileName = t.profileName;
    }

    // -------- Interval interface ---------

    Long cuttedEnd = null;

    public long durationInMsec() {
        return durationInMinutes * 60 * 1000L;
    }

    public long start() {
        return date;
    }

    // planned end time at time of creation
    public long originalEnd() {
        return date + durationInMinutes * 60 * 1000L;
    }

    // end time after cut
    public long end() {
        if (cuttedEnd != null)
            return cuttedEnd;
        return originalEnd();
    }

    public void cutEndTo(long end) {
        cuttedEnd = end;
    }

    public boolean match(long time) {
        if (start() <= time && end() >= time)
            return true;
        return false;
    }

    public boolean before(long time) {
        if (end() < time)
            return true;
        return false;
    }

    public boolean after(long time) {
        if (start() > time)
            return true;
        return false;
    }

    @Override
    public boolean isInProgress() {
        return match(System.currentTimeMillis());
    }

    @Override
    public boolean isEndingEvent() {
        return durationInMinutes == 0;
    }

    // -------- Interval interface end ---------

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
    public void setY(double y) {
        yValue = y;
    }

    @Override
    public String getLabel() {
        return getCustomizedName();
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        return PointsWithLabelGraphSeries.Shape.PROFILE;
    }

    @Override
    public float getSize() {
        return 10;
    }

    @Override
    public int getColor() {
        return Color.CYAN;
    }

    public String toString() {
        return "ProfileSwitch{" +
                "date=" + date +
                "date=" + DateUtil.dateAndTimeString(date) +
                ", isValid=" + isValid +
                ", duration=" + durationInMinutes +
                ", profileName=" + profileName +
                ", percentage=" + percentage +
                ", timeshift=" + timeshift +
                '}';
    }

}
