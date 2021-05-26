package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 21.05.2017.
 */

@Deprecated
@DatabaseTable(tableName = "TemporaryBasals")
public class TemporaryBasal implements Interval {

    @Inject public AAPSLogger aapsLogger;
    @Inject public ProfileFunction profileFunction;
    @Inject public ActivePlugin activePlugin;
    @Inject public SP sp;
    @Inject public DateUtil dateUtil;

    private HasAndroidInjector injector;

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField(index = true)
    public long pumpId = 0;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id = null; // NS _id

    @DatabaseField
    public int durationInMinutes = 0; // duration == 0 means end of temp basal
    @DatabaseField
    public boolean isAbsolute = false;

    public boolean isFakeExtended = false;

    @DatabaseField
    public int percentRate = 0;
    @DatabaseField
    public double absoluteRate = 0d;

    public double netExtendedRate = 0d;

    @Deprecated
    public TemporaryBasal() {
        injector = StaticInjector.Companion.getInstance();
        injector.androidInjector().inject(this);
    }

    public TemporaryBasal(HasAndroidInjector injector) {
        this.injector = injector;
        injector.androidInjector().inject(this);
    }

    public TemporaryBasal date(long date) {
        this.date = date;
        return this;
    }

    public TemporaryBasal duration(int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }

    public TemporaryBasal absolute(double absoluteRate) {
        this.absoluteRate = absoluteRate;
        this.isAbsolute = true;
        return this;
    }

    public TemporaryBasal percent(int percentRate) {
        this.percentRate = percentRate;
        this.isAbsolute = false;
        return this;
    }

    public TemporaryBasal source(int source) {
        this.source = source;
        return this;
    }

    public TemporaryBasal pumpId(long pumpId) {
        this.pumpId = pumpId;
        return this;
    }

    public TemporaryBasal(ExtendedBolus extendedBolus) {
        injector = StaticInjector.Companion.getInstance();
        injector.androidInjector().inject(this);
        double basal = profileFunction.getProfile(extendedBolus.date).getBasal(extendedBolus.date);
        this.date = extendedBolus.date;
        this.isValid = extendedBolus.isValid;
        this.source = extendedBolus.source;
        this._id = extendedBolus._id;
        this.durationInMinutes = extendedBolus.durationInMinutes;
        this.isAbsolute = true;
        this.isFakeExtended = true;
        this.netExtendedRate = extendedBolus.absoluteRate();
        this.absoluteRate = basal + extendedBolus.absoluteRate();
        this.pumpId = extendedBolus.pumpId;
    }

    public TemporaryBasal clone() {
        TemporaryBasal t = new TemporaryBasal(injector);
        t.date = date;
        t.isValid = isValid;
        t.source = source;
        t._id = _id;
        t.pumpId = pumpId;
        t.durationInMinutes = durationInMinutes;
        t.isAbsolute = isAbsolute;
        t.percentRate = percentRate;
        t.absoluteRate = absoluteRate;
        return t;
    }

    public boolean isEqual(TemporaryBasal other) {
        if (date != other.date) {
            return false;
        }
        if (durationInMinutes != other.durationInMinutes)
            return false;
        if (isAbsolute != other.isAbsolute)
            return false;
        if (percentRate != other.percentRate)
            return false;
        if (absoluteRate != other.absoluteRate)
            return false;
        if (netExtendedRate != other.netExtendedRate)
            return false;
        if (isFakeExtended != other.isFakeExtended)
            return false;
        if (pumpId != other.pumpId)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public void copyFrom(TemporaryBasal t) {
        date = t.date;
        _id = t._id;
        durationInMinutes = t.durationInMinutes;
        isAbsolute = t.isAbsolute;
        percentRate = t.percentRate;
        absoluteRate = t.absoluteRate;
        pumpId = t.pumpId;
        isFakeExtended = t.isFakeExtended;
        netExtendedRate = t.netExtendedRate;
    }

    public void copyFromPump(TemporaryBasal t) {
        durationInMinutes = t.durationInMinutes;
        isAbsolute = t.isAbsolute;
        percentRate = t.percentRate;
        absoluteRate = t.absoluteRate;
        pumpId = t.pumpId;
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

    public int getRealDuration() {
        return getDurationToTime(System.currentTimeMillis());
    }

    private int getDurationToTime(long time) {
        long endTime = Math.min(time, end());
        long msecs = endTime - date;
        return Math.round(msecs / 60f / 1000);
    }

    public int getPlannedRemainingMinutes() {
        float remainingMin = (end() - System.currentTimeMillis()) / 1000f / 60;
        return (remainingMin < 0) ? 0 : Math.round(remainingMin);
    }

    public int getPlannedRemainingMinutesRoundedUp() {
        float remainingMin = (end() - System.currentTimeMillis()) / 1000f / 60;
        return (remainingMin < 0) ? 0 : (int) Math.ceil(remainingMin);
    }


    public double tempBasalConvertedToAbsolute(long time, Profile profile) {
        if (isFakeExtended) {
            return profile.getBasal(time) + netExtendedRate;
        } else if (isAbsolute) {
            return absoluteRate;
        } else {
            return profile.getBasal(time) * percentRate / 100;
        }
    }

    public int tempBasalConvertedToPercent(long time, Profile profile) {
        if (isFakeExtended) {
            return (int) ((profile.getBasal(time) + netExtendedRate) / profile.getBasal(time)) * 100;
        } else if (isAbsolute) {
            return (int) (absoluteRate / profile.getBasal(time) * 100);
        } else {
            return percentRate;
        }
    }

    public String toString() {
        return "TemporaryBasal{" +
                "date=" + date +
                ", date=" + dateUtil.dateAndTimeString(date) +
                ", isValid=" + isValid +
                ", pumpId=" + pumpId +
                ", _id=" + _id +
                ", percentRate=" + percentRate +
                ", absoluteRate=" + absoluteRate +
                ", durationInMinutes=" + durationInMinutes +
                ", isAbsolute=" + isAbsolute +
                ", isFakeExtended=" + isFakeExtended +
                ", netExtendedRate=" + netExtendedRate +
                '}';
    }

    public String toStringFull() {
        if (isFakeExtended) {

            Profile profile = profileFunction.getProfile();
            if (profile == null)
                return "null";
            Double currentBasalRate = profile.getBasal();
            double rate = currentBasalRate + netExtendedRate;
            return DecimalFormatter.INSTANCE.to2Decimal(rate) + "U/h (" + DecimalFormatter.INSTANCE.to2Decimal(netExtendedRate) + "E) @" +
                    dateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "'";
        } else if (isAbsolute) {
            return DecimalFormatter.INSTANCE.to2Decimal(absoluteRate) + "U/h @" +
                    dateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "'";
        } else { // percent
            return percentRate + "% @" +
                    dateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "'";
        }
    }

    public String toStringShort() {
        if (isAbsolute || isFakeExtended) {

            double rate;
            if (isFakeExtended) {
                Profile profile = profileFunction.getProfile();
                if (profile == null)
                    return "null";
                double currentBasalRate = profile.getBasal();
                rate = currentBasalRate + netExtendedRate;
            } else {
                rate = absoluteRate;
            }

            return DecimalFormatter.INSTANCE.to2Decimal(rate) + "U/h";
        } else { // percent
            return percentRate + "%";
        }
    }

    public String toStringVeryShort() {
        Profile profile = profileFunction.getProfile();

        if (profile == null)
            return "null";

        if (isAbsolute || isFakeExtended) {

            double rate;
            if (isFakeExtended) {
                double currentBasalRate = profile.getBasal();
                rate = currentBasalRate + netExtendedRate;
            } else {
                rate = absoluteRate;
            }
            return DecimalFormatter.INSTANCE.to2Decimal(rate) + "U/h";
        } else { // percent
            return percentRate + "%";
        }
    }

    public long getDate() {
        return this.date;
    }

    public long getPumpId() {
        return this.pumpId;
    }
}
