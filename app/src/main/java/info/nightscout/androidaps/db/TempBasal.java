package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPBASALS)
public class TempBasal {
    private static Logger log = LoggerFactory.getLogger(TempBasal.class);

    public long getTimeIndex() {
        return timeStart.getTime();
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;

    @DatabaseField
    public Date timeStart;

    @DatabaseField
    public Date timeEnd;

    @DatabaseField
    public int percent;     // In % of current basal.  100% == current basal

    @DatabaseField
    public Double absolute;    // Absolute value in U

    @DatabaseField
    public int duration;    // in minutes

    @DatabaseField
    public boolean isExtended = false; // true if set as extended bolus

    @DatabaseField
    public boolean isAbsolute = false; // true if if set as absolute value in U


    public IobTotal iobCalc(long time) {
        IobTotal result = new IobTotal(time);
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getActiveInsulin();

        if (profile == null)
            return result;

        int realDuration = getDurationToTime(time);
        Double netBasalAmount = 0d;

        if (realDuration > 0) {
            Double netBasalRate = 0d;

            Double dia_ago = time - profile.getDia() * 60 * 60 * 1000;
            int aboutFiveMinIntervals = (int) Math.ceil(realDuration / 5d);
            double tempBolusSpacing = realDuration / aboutFiveMinIntervals;

            for (Long j = 0L; j < aboutFiveMinIntervals; j++) {
                // find middle of the interval
                Long date = (long) (timeStart.getTime() + j * tempBolusSpacing * 60 * 1000 + 0.5d * tempBolusSpacing * 60 * 1000);

                Double basalRate = profile.getBasal(NSProfile.secondsFromMidnight(date));

                if (basalRate == null)
                    continue;
                if (isExtended) {
                    netBasalRate = this.absolute;
                } else {
                    if (this.isAbsolute) {
                        netBasalRate = this.absolute - basalRate;
                    } else {
                        netBasalRate = (this.percent - 100) / 100d * basalRate;
                    }
                }

                if (date > dia_ago && date <= time) {
                    double tempBolusSize = netBasalRate * tempBolusSpacing / 60d;
                    netBasalAmount += tempBolusSize;

                    Treatment tempBolusPart = new Treatment(insulinInterface);
                    tempBolusPart.insulin = tempBolusSize;
                    tempBolusPart.created_at = new Date(date);

                    Iob aIOB = insulinInterface.iobCalc(tempBolusPart, time, profile.getDia());
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

/*
    public IobTotal old_iobCalc(long time) {
        IobTotal result = new IobTotal(time);
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getActiveInsulin();

        if (profile == null)
            return result;

        Double basalRate = profile.getBasal(NSProfile.secondsFromMidnight(time));

        if (basalRate == null)
            return result;

        int realDuration = getDurationToTime(time);

        if (realDuration > 0) {
            Double netBasalRate = 0d;
            Double tempBolusSize = 0.05;

            if (isExtended) {
                netBasalRate = this.absolute;
            } else {
                if (this.isAbsolute) {
                    netBasalRate = this.absolute - basalRate;
                } else {
                    netBasalRate = (this.percent - 100) / 100d * basalRate;
                }
            }

            result.netRatio = netBasalRate;
            Double netBasalAmount = Math.round(netBasalRate * realDuration * 10 / 6) / 100d;
            result.netInsulin = netBasalAmount;
            if (netBasalAmount < 0.1) {
                tempBolusSize = 0.01;
            }
            if (netBasalRate < 0) {
                tempBolusSize = -tempBolusSize;
            }
            Long tempBolusCount = Math.round(netBasalAmount / tempBolusSize);
            if (tempBolusCount > 0) {
                Long tempBolusSpacing = realDuration / tempBolusCount;
                for (Long j = 0L; j < tempBolusCount; j++) {
                    Treatment tempBolusPart = new Treatment(insulinInterface);
                    tempBolusPart.insulin = tempBolusSize;
                    Long date = this.timeStart.getTime() + j * tempBolusSpacing * 60 * 1000;
                    tempBolusPart.created_at = new Date(date);

                    Iob aIOB = insulinInterface.iobCalc(tempBolusPart, time, profile.getDia());
                    result.basaliob += aIOB.iobContrib;
                    result.activity += aIOB.activityContrib;
                    Double dia_ago = time - profile.getDia() * 60 * 60 * 1000;
                    if (date > dia_ago && date <= time) {
                        result.netbasalinsulin += tempBolusPart.insulin;
                        if (tempBolusPart.insulin > 0) {
                            result.hightempinsulin += tempBolusPart.insulin;
                        }
                    }
                }
            }
        }
        return result;
    }
*/

    // Determine end of basal
    public long getTimeEnd() {
        long tempBasalTimePlannedEnd = getPlannedTimeEnd();
        long now = new Date().getTime();

        if (timeEnd != null && timeEnd.getTime() < tempBasalTimePlannedEnd) {
            tempBasalTimePlannedEnd = timeEnd.getTime();
        }

        if (now < tempBasalTimePlannedEnd)
            tempBasalTimePlannedEnd = now;

        return tempBasalTimePlannedEnd;
    }

    public long getPlannedTimeEnd() {
        return timeStart.getTime() + 60 * 1_000 * duration;
    }

    public int getRealDuration() {
        long msecs = getTimeEnd() - timeStart.getTime();
        return Math.round(msecs / 60f / 1000);
    }

    public int getDurationToTime(long time) {
        long endTime = Math.min(time, getTimeEnd());
        long msecs = endTime - timeStart.getTime();
        return Math.round(msecs / 60f / 1000);
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - timeStart.getTime();
    }

    public int getPlannedRemainingMinutes() {
        if (timeEnd != null) return 0;
        float remainingMin = (getPlannedTimeEnd() - new Date().getTime()) / 1000f / 60;
        return (remainingMin < 0) ? 0 : Math.round(remainingMin);
    }

    public boolean isInProgress() {
        return isInProgress(new Date());
    }

    public double tempBasalConvertedToAbsolute(Date time) {
        if (isExtended) {
            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            double absval = profile.getBasal(NSProfile.secondsFromMidnight(time)) + absolute;
            return absval;
        } else {
            if (isAbsolute) return absolute;
            else {
                NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
                double absval = profile.getBasal(NSProfile.secondsFromMidnight(time)) * percent / 100;
                return absval;
            }
        }
    }

    public boolean isInProgress(Date time) {
        if (timeStart.getTime() > time.getTime()) return false; // in the future
        if (timeEnd == null) { // open end
            if (timeStart.getTime() < time.getTime() && getPlannedTimeEnd() > time.getTime())
                return true; // in interval
            return false;
        }
        // closed end
        if (timeStart.getTime() < time.getTime() && timeEnd.getTime() > time.getTime())
            return true; // in interval
        return false;
    }

    public String log() {
        return "TempBasal{" +
                "timeIndex=" + timeIndex +
                ", timeStart=" + timeStart +
                ", timeEnd=" + timeEnd +
                ", percent=" + percent +
                ", absolute=" + absolute +
                ", duration=" + duration +
                ", isAbsolute=" + isAbsolute +
                ", isExtended=" + isExtended +
                '}';
    }

    public String toString() {
        String extended = isExtended ? "E " : "";

        if (isAbsolute) {
            return extended + DecimalFormatter.to2Decimal(absolute) + "U/h @" +
                    DateUtil.timeString(timeStart) +
                    " " + getRealDuration() + "/" + duration + "min";
        } else { // percent
            return percent + "% @" +
                    DateUtil.timeString(timeStart) +
                    " " + getRealDuration() + "/" + duration + "min";
        }
    }

    public String toStringShort() {
        String extended = isExtended ? "E" : "";

        if (isAbsolute) {
            return extended + DecimalFormatter.to2Decimal(absolute) + "U/h ";
        } else { // percent
            return percent + "% ";
        }
    }

    public String toStringMedium() {
        String extended = isExtended ? "E" : "";

        if (isAbsolute) {
            return extended + DecimalFormatter.to2Decimal(absolute) + "U/h ("
                    + getRealDuration() + "/" + duration + ") ";
        } else { // percent
            return percent + "% (" + getRealDuration() + "/" + duration + ") ";
        }
    }

}
