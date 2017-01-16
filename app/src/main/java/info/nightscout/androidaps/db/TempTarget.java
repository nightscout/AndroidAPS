package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.utils.DecimalFormatter;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TEMPTARGETS)
public class TempTarget {
    private static Logger log = LoggerFactory.getLogger(TempTarget.class);

    public long getTimeIndex() {
        return timeStart.getTime() - timeStart.getTime() % 1000;
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;

    @DatabaseField
    public Date timeStart;

    @DatabaseField
    public double low; // in mgdl

    @DatabaseField
    public double high; // in mgdl

    @DatabaseField
    public String reason;

    @DatabaseField
    public int duration;    // in minutes

    @DatabaseField
    public String _id;    // NS _id

    public Date getPlannedTimeEnd() {
        return new Date(timeStart.getTime() + 60 * 1_000 * duration);
    }

    public String lowValueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(low);
        else return DecimalFormatter.to1Decimal(low * Constants.MGDL_TO_MMOLL);
    }

    public String highValueToUnitsToString(String units) {
        if (units.equals(Constants.MGDL)) return DecimalFormatter.to0Decimal(high);
        else return DecimalFormatter.to1Decimal(low * Constants.MGDL_TO_MMOLL);
    }

    public boolean isInProgress() {
        return ((TempTargetRangePlugin) MainApp.getSpecificPlugin(TempTargetRangePlugin.class)).getTempTargetInProgress(new Date().getTime()) == this;
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
