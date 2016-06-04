package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

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
    public int percent;     // In % of current basal

    @DatabaseField
    public int absolute;    // Absolute value in U

    @DatabaseField
    public int duration;    // in minutes

    @DatabaseField
    public boolean isExtended; // true if set as extended bolus

    @DatabaseField
    public boolean isAbsolute; // true if if set as absolute value in U

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

        // End already exists in database
        if (timeEnd != null) {
            return timeEnd;
        }

        // if not return planned time
        return tempBasalTimePlannedEnd;
    }

    public Date getPlannedTimeEnd() {
        return new Date(timeStart.getTime() + 60 * 1_000 * duration);
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - timeStart.getTime();
    }

    public int getRemainingMinutes() {
        long remainingMin = (getTimeEnd().getTime() - new Date().getTime()) / 1000 / 60;
        return (remainingMin < 0) ? 0 : (int) remainingMin;
    }

    @Override
    public String toString() {
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
}
