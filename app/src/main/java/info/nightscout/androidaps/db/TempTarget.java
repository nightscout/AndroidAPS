package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPTARGETS)
public class TempTarget implements Interval {
    private static Logger log = LoggerFactory.getLogger(TempTarget.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id = null; // NS _id

    @DatabaseField
    public double low; // in mgdl

    @DatabaseField
    public double high; // in mgdl

    @DatabaseField
    public String reason;

    @DatabaseField
    public int durationInMinutes;

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

    public String lowValueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(low);
        else return DecimalFormatter.to1Decimal(low * Constants.MGDL_TO_MMOLL);
    }

    public String highValueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(high);
        else return DecimalFormatter.to1Decimal(low * Constants.MGDL_TO_MMOLL);
    }

    public String log() {
        return "TemporaryTarget{" +
                "date=" + date +
                "date=" + DateUtil.dateAndTimeString(date) +
                ", isValid=" + isValid +
                ", duration=" + durationInMinutes +
                ", reason=" + reason +
                ", low=" + low +
                ", high=" + high +
                '}';
    }

}
