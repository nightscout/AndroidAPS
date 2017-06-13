package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 21.05.2017.
 */

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPORARYBASALS)
public class TemporaryBasal implements Interval {
    private static Logger log = LoggerFactory.getLogger(TemporaryBasal.class);

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
    @DatabaseField
    public int percentRate = 0;
    @DatabaseField
    public double absoluteRate = 0d;

    public TemporaryBasal() {
    }

    public TemporaryBasal(long date) {
        this.date = date;
    }

    public TemporaryBasal(ExtendedBolus extendedBolus) {
        double basal = MainApp.getConfigBuilder().getProfile(extendedBolus.date).getBasal(extendedBolus.date);
        this.date = extendedBolus.date;
        this.isValid = extendedBolus.isValid;
        this.source = extendedBolus.source;
        this._id = extendedBolus._id;
        this.durationInMinutes = extendedBolus.durationInMinutes;
        this.isAbsolute = true;
        this.absoluteRate = basal + extendedBolus.absoluteRate();
    }

    public TemporaryBasal clone() {
        TemporaryBasal t = new TemporaryBasal();
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
        return match(new Date().getTime());
    }

    @Override
    public boolean isEndingEvent() {
        return durationInMinutes == 0;
    }

    // -------- Interval interface end ---------

    public IobTotal iobCalc(long time) {
        IobTotal result = new IobTotal(time);
        Profile profile = MainApp.getConfigBuilder().getProfile(time);
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getActiveInsulin();

       int realDuration = getDurationToTime(time);
        Double netBasalAmount = 0d;

        if (realDuration > 0) {
            Double netBasalRate = 0d;
            double dia = profile.getDia();
            Double dia_ago = time - dia * 60 * 60 * 1000;
            int aboutFiveMinIntervals = (int) Math.ceil(realDuration / 5d);
            double tempBolusSpacing = realDuration / aboutFiveMinIntervals;

            for (Long j = 0L; j < aboutFiveMinIntervals; j++) {
                // find middle of the interval
                Long calcdate = (long) (date + j * tempBolusSpacing * 60 * 1000 + 0.5d * tempBolusSpacing * 60 * 1000);

                Double basalRate = profile.getBasal(calcdate);

                if (basalRate == null)
                    continue;
                if (isAbsolute) {
                    netBasalRate = absoluteRate - basalRate;
                } else {
                    netBasalRate = (percentRate - 100) / 100d * basalRate;
                }

                if (calcdate > dia_ago && calcdate <= time) {
                    double tempBolusSize = netBasalRate * tempBolusSpacing / 60d;
                    netBasalAmount += tempBolusSize;

                    Treatment tempBolusPart = new Treatment(insulinInterface, dia);
                    tempBolusPart.insulin = tempBolusSize;
                    tempBolusPart.date = calcdate;

                    Iob aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia);
                    result.basaliob += aIOB.iobContrib;
                    result.activity += aIOB.activityContrib;
                    result.netbasalinsulin += tempBolusPart.insulin;
                    if (tempBolusPart.insulin > 0) {
                        result.hightempinsulin += tempBolusPart.insulin;
                    }
                }
                result.netRatio = netBasalRate; // ratio at the end of interval
            }
        }
        result.netInsulin = netBasalAmount;
        return result;
    }

    public int getRealDuration() {
        return getDurationToTime(new Date().getTime());
    }

    private int getDurationToTime(long time) {
        long endTime = Math.min(time, end());
        long msecs = endTime - date;
        return Math.round(msecs / 60f / 1000);
    }

    public int getPlannedRemainingMinutes() {
        float remainingMin = (end() - new Date().getTime()) / 1000f / 60;
        return (remainingMin < 0) ? 0 : Math.round(remainingMin);
    }

    public double tempBasalConvertedToAbsolute(long time) {
        if (isAbsolute) return absoluteRate;
        else {
             return MainApp.getConfigBuilder().getProfile(time).getBasal(time) * percentRate / 100;
        }
    }

    public String toString() {
        return "TemporaryBasal{" +
                "date=" + date +
                ", date=" + DateUtil.dateAndTimeString(date) +
                ", isValid=" + isValid +
                ", pumpId=" + pumpId +
                ", _id=" + _id +
                ", percentRate=" + percentRate +
                ", absoluteRate=" + absoluteRate +
                ", durationInMinutes=" + durationInMinutes +
                ", isAbsolute=" + isAbsolute +
                '}';
    }

    public String toStringFull() {
        if (isAbsolute) {
            return DecimalFormatter.to2Decimal(absoluteRate) + "U/h @" +
                    DateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "min";
        } else { // percent
            return percentRate + "% @" +
                    DateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "min";
        }
    }

    public String toStringShort() {
        if (isAbsolute) {
            return DecimalFormatter.to2Decimal(absoluteRate) + "U/h ";
        } else { // percent
            return percentRate + "% ";
        }
    }

    public String toStringMedium() {
        if (isAbsolute) {
            return DecimalFormatter.to2Decimal(absoluteRate) + "U/h ("
                    + getRealDuration() + "/" + durationInMinutes + ") ";
        } else { // percent
            return percentRate + "% (" + getRealDuration() + "/" + durationInMinutes + ") ";
        }
    }

}
