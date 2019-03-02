package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPTARGETS)
public class TempTarget implements Interval {
    private static Logger log = LoggerFactory.getLogger(L.DATABASE);

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

    public double target() {
        return (low + high) / 2;
    }

    public boolean isEqual(TempTarget other) {
        if (date != other.date) {
            return false;
        }
        if (durationInMinutes != other.durationInMinutes)
            return false;
        if (low != other.low)
            return false;
        if (high != other.high)
            return false;
        if (!Objects.equals(reason, other.reason))
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public void copyFrom(TempTarget t) {
        date = t.date;
        _id = t._id;
        durationInMinutes = t.durationInMinutes;
        low = t.low;
        high = t.high;
        reason = t.reason;
    }

    public TempTarget date(long date) {
        this.date = date;
        return this;
    }

    public TempTarget low(double low) {
        this.low = low;
        return this;
    }

    public TempTarget high(double high) {
        this.high = high;
        return this;
    }

    public TempTarget duration(int duration) {
        this.durationInMinutes = duration;
        return this;
    }

    public TempTarget reason(String reason) {
        this.reason = reason;
        return this;
    }

    public TempTarget _id(String _id) {
        this._id = _id;
        return this;
    }

    public TempTarget source(int source) {
        this.source = source;
        return this;
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

    @Override
    public boolean isValid() {
        return true;
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

    public String toString() {
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
