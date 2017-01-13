package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPTARGETS)
public class TempTarget {
    private static Logger log = LoggerFactory.getLogger(TempTarget.class);

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
    public double low;

    @DatabaseField
    public double high;

    @DatabaseField
    public String reason;

    @DatabaseField
    public int duration;    // in minutes

    public Date getPlannedTimeEnd() {
        return new Date(timeStart.getTime() + 60 * 1_000 * duration);
    }

    public String log() {
        return "TempTarget{" +
                "timeIndex=" + timeIndex +
                ", timeStart=" + timeStart +
                ", duration=" + duration +
                ", reason=" + reason +
                ", low=" + low +
                ", high=" + high +
                '}';
    }

}
