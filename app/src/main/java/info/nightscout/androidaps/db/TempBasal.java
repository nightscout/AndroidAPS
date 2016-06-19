package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.client.data.NSProfile;

@DatabaseTable(tableName = "TempBasals")
public class TempBasal {
    private static Logger log = LoggerFactory.getLogger(TempBasal.class);

    public long getTimeIndex() {
        return (long) Math.ceil(timeStart.getTime() / 60000d);
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


    public IobTotal iobCalc(Date time) {
        IobTotal result = new IobTotal();
        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();

        if (profile == null)
            return result;

        int realDuration = getRealDuration();

        if (realDuration > 0) {
            Double netBasalRate = 0d;
            Double basalRate = profile.getBasal(profile.secondsFromMidnight(time));
            Double tempBolusSize = 0.05;

            if (this.isAbsolute) {
                netBasalRate = this.absolute - basalRate;
            } else {
                netBasalRate = (this.percent - 100) / 100d * basalRate;
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
                for (Long j = 0l; j < tempBolusCount; j++) {
                    Treatment tempBolusPart = new Treatment();
                    tempBolusPart.insulin = tempBolusSize;
                    Long date = this.timeStart.getTime() + j * tempBolusSpacing * 60 * 1000;
                    tempBolusPart.created_at = new Date(date);

                    Iob aIOB = tempBolusPart.iobCalc(time, profile.getDia());
                    result.basaliob += aIOB.iobContrib;
                    Double dia_ago = time.getTime() - profile.getDia() * 60 * 60 * 1000;
                    if (date > dia_ago && date <= time.getTime()) {
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

    /*
        public Iob calcIob() {
            Iob iob = new Iob();

            long msAgo = getMillisecondsFromStart();
            Calendar startAdjusted = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startAdjusted.setTime(this.timeStart);
            int minutes = startAdjusted.get(Calendar.MINUTE);
            minutes = minutes % 4;
            if (startAdjusted.get(Calendar.SECOND) > 0 && minutes == 0) {
                minutes += 4;
            }
            startAdjusted.add(Calendar.MINUTE, minutes);
            startAdjusted.set(Calendar.SECOND, 0);

            IobCalc iobCalc = new IobCalc();
            iobCalc.setTime(new Date());
            iobCalc.setAmount(-1.0d * (baseRatio - tempRatio) / 15.0d / 100.0d);

            long timeStartTime = startAdjusted.getTimeInMillis();
            Date currentTimeEnd = timeEnd;
            if (currentTimeEnd == null) {
                currentTimeEnd = new Date();
                if (getPlannedTimeEnd().getTime() < currentTimeEnd.getTime()) {
                    currentTimeEnd = getPlannedTimeEnd();
                }
            }
            for (long time = timeStartTime; time < currentTimeEnd.getTime(); time += 4 * 60_000) {
                Date start = new Date(time);

                iobCalc.setTimeStart(start);
                iob.plus(iobCalc.invoke());
            }

            if (Settings.logTempIOBCalculation) {
                log.debug("TempIOB start: " + this.timeStart + " end: " + this.timeEnd + " Percent: " + this.percent + " Duration: " + this.duration + " CalcDurat: " + (int) ((currentTimeEnd.getTime() - this.timeStart.getTime()) / 1000 / 60)
                        + "min minAgo: " + (int) (msAgo / 1000 / 60) + " IOB: " + iob.iobContrib + " Activity: " + iob.activityContrib + " Impact: " + (-0.01d * (baseRatio - tempRatio) * ((currentTimeEnd.getTime() - this.timeStart.getTime()) / 1000 / 60) / 60)
                );
            }

            return iob;
        }
    */
    // Determine end of basal
    public Date getTimeEnd() {
        Date tempBasalTimePlannedEnd = getPlannedTimeEnd();
        Date now = new Date();

        if (timeEnd != null && timeEnd.getTime() < tempBasalTimePlannedEnd.getTime()) {
            tempBasalTimePlannedEnd = timeEnd;
        }

        if (now.getTime() < tempBasalTimePlannedEnd.getTime())
            tempBasalTimePlannedEnd = now;

        return tempBasalTimePlannedEnd;
    }

    public Date getPlannedTimeEnd() {
        return new Date(timeStart.getTime() + 60 * 1_000 * duration);
    }

    public int getRealDuration() {
        Long msecs = getTimeEnd().getTime() - timeStart.getTime();
        return (int) (msecs / 60 / 1000);
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - timeStart.getTime();
    }

    public int getRemainingMinutes() {
        long remainingMin = (getTimeEnd().getTime() - new Date().getTime()) / 1000 / 60;
        return (remainingMin < 0) ? 0 : (int) remainingMin;
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
        DateFormat formatDateToJustTime = new SimpleDateFormat("HH:mm");
        DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

        if (isAbsolute) {
            return formatNumber2decimalplaces.format(absolute) + "U/h @" +
                    formatDateToJustTime.format(timeStart) +
                    " " + getRealDuration() + "/" + duration + "min";
        } else { // percent
            return percent + "% @" +
                    formatDateToJustTime.format(timeStart) +
                    " " + getRealDuration() + "/" + duration + "min";
        }
    }

}
