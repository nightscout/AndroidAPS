package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.utils.DateUtil;

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
        return match(new Date().getTime());
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
    public String getLabel() {
        return profileName;
    }

    public String log() {
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
