package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 21.05.2017.
 */

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPORARYBASALS)
public class TemporaryBasal implements Interval, DbObjectBase {
    private static Logger log = LoggerFactory.getLogger(L.DATABASE);

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

    public TemporaryBasal() {
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
        double basal = ProfileFunctions.getInstance().getProfile(extendedBolus.date).getBasal(extendedBolus.date);
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

    public IobTotal iobCalc(long time, Profile profile) {

        if (isFakeExtended) {
            log.error("iobCalc should only be called on Extended boluses separately");
            return new IobTotal(time);
        }

        IobTotal result = new IobTotal(time);
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();

        int realDuration = getDurationToTime(time);
        double netBasalAmount = 0d;

        if (realDuration > 0) {
            double netBasalRate;
            double dia = profile.getDia();
            double dia_ago = time - dia * 60 * 60 * 1000;
            int aboutFiveMinIntervals = (int) Math.ceil(realDuration / 5d);
            double tempBolusSpacing = realDuration / aboutFiveMinIntervals;

            for (long j = 0L; j < aboutFiveMinIntervals; j++) {
                // find middle of the interval
                long calcdate = (long) (date + j * tempBolusSpacing * 60 * 1000 + 0.5d * tempBolusSpacing * 60 * 1000);

                double basalRate = profile.getBasal(calcdate);

                if (isAbsolute) {
                    netBasalRate = absoluteRate - basalRate;
                } else {
                    netBasalRate = (percentRate - 100) / 100d * basalRate;
                }

                if (calcdate > dia_ago && calcdate <= time) {
                    double tempBolusSize = netBasalRate * tempBolusSpacing / 60d;
                    netBasalAmount += tempBolusSize;

                    Treatment tempBolusPart = new Treatment();
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

    public IobTotal iobCalc(long time, Profile profile, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {

        if (isFakeExtended) {
            log.error("iobCalc should only be called on Extended boluses separately");
            return new IobTotal(time);
        }

        IobTotal result = new IobTotal(time);
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();

        double realDuration = getDurationToTime(time);
        double netBasalAmount = 0d;

        double sensitivityRatio = lastAutosensResult.ratio;
        double normalTarget = 100;

        if (exercise_mode && isTempTarget && profile.getTarget() >= normalTarget + 5) {
            // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
            // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
            double c = half_basal_exercise_target - normalTarget;
            sensitivityRatio = c / (c + profile.getTarget() - normalTarget);
        }

        if (realDuration > 0) {
            double netBasalRate;
            double dia = profile.getDia();
            double dia_ago = time - dia * 60 * 60 * 1000;
            int aboutFiveMinIntervals = (int) Math.ceil(realDuration / 5d);
            double tempBolusSpacing = realDuration / aboutFiveMinIntervals;

            for (long j = 0L; j < aboutFiveMinIntervals; j++) {
                // find middle of the interval
                long calcdate = (long) (date + j * tempBolusSpacing * 60 * 1000 + 0.5d * tempBolusSpacing * 60 * 1000);

                double basalRate = profile.getBasal(calcdate);
                basalRate *= sensitivityRatio;

                if (isAbsolute) {
                    netBasalRate = absoluteRate - basalRate;
                } else {
                    double abs = percentRate / 100d * profile.getBasal(calcdate);
                    netBasalRate = abs - basalRate;
                }

                if (calcdate > dia_ago && calcdate <= time) {
                    double tempBolusSize = netBasalRate * tempBolusSpacing / 60d;
                    netBasalAmount += tempBolusSize;

                    Treatment tempBolusPart = new Treatment();
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
            return (int) (absoluteRate / profile.getBasal(time)) * 100;
        } else {
            return percentRate;
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
                ", isFakeExtended=" + isFakeExtended +
                ", netExtendedRate=" + netExtendedRate +
                '}';
    }

    public String toStringFull() {
        if (isFakeExtended) {

            Profile profile = ProfileFunctions.getInstance().getProfile();
            if (profile == null)
                return "null";
            Double currentBasalRate = profile.getBasal();
            double rate = currentBasalRate + netExtendedRate;
            return getCalcuatedPercentageIfNeeded() + DecimalFormatter.to2Decimal(rate) + "U/h (" + DecimalFormatter.to2Decimal(netExtendedRate) + "E) @" +
                    DateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "'";
        } else if (isAbsolute) {
            return DecimalFormatter.to2Decimal(absoluteRate) + "U/h @" +
                    DateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "'";
        } else { // percent
            return percentRate + "% @" +
                    DateUtil.timeString(date) +
                    " " + getRealDuration() + "/" + durationInMinutes + "'";
        }
    }

    public String toStringShort() {
        if (isAbsolute || isFakeExtended) {

            double rate;
            if (isFakeExtended) {
                Profile profile = ProfileFunctions.getInstance().getProfile();
                if (profile == null)
                    return "null";
                double currentBasalRate = profile.getBasal();
                rate = currentBasalRate + netExtendedRate;
            } else {
                rate = absoluteRate;
            }

            if (SP.getBoolean(R.string.key_danar_visualizeextendedaspercentage, false) && SP.getBoolean(R.string.key_danar_useextended, false)) {
                Profile profile = ProfileFunctions.getInstance().getProfile();
                if (profile != null) {
                    double basal = profile.getBasal();
                    if (basal != 0) {
                        return Math.round(rate * 100d / basal) + "%";
                    }
                }
            }
            return DecimalFormatter.to2Decimal(rate) + "U/h";
        } else { // percent
            return percentRate + "%";
        }
    }

    private String getCalcuatedPercentageIfNeeded() {
        Profile profile = ProfileFunctions.getInstance().getProfile();

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

            if (SP.getBoolean(R.string.key_danar_visualizeextendedaspercentage, false) && SP.getBoolean(R.string.key_danar_useextended, false)) {
                double basal = profile.getBasal();
                if (basal != 0) {
                    return Math.round(rate * 100d / basal) + "% ";
                }
            }
        }
        return "";
    }

    public String toStringVeryShort() {
        Profile profile = ProfileFunctions.getInstance().getProfile();

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
            return DecimalFormatter.to2Decimal(rate) + "U/h ";
        } else { // percent
            return percentRate + "% ";
        }
    }

    @Override
    public long getDate() {
        return this.date;
    }

    @Override
    public long getPumpId() {
        return this.pumpId;
    }
}
