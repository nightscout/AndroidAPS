package info.nightscout.androidaps.db;

/**
 * Created by mike on 21.05.2017.
 */

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.Round;

/**
 * Created by mike on 21.05.2017.
 */

@DatabaseTable(tableName = DatabaseHelper.DATABASE_EXTENDEDBOLUSES)
public class ExtendedBolus implements Interval, DataPointWithLabelInterface {
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
    public double insulin = 0d;
    @DatabaseField
    public int durationInMinutes = 0; // duration == 0 means end of extended bolus

    @DatabaseField
    public int insulinInterfaceID = InsulinInterface.OREF_RAPID_ACTING;
    @DatabaseField
    public double dia = Constants.defaultDIA;

    public ExtendedBolus() {
    }

    public ExtendedBolus(long date) {
        this.date = date;
    }

    public ExtendedBolus date(long date) {
        this.date = date;
        return this;
    }

    public ExtendedBolus insulin(double insulin) {
        this.insulin = insulin;
        return this;
    }

    public ExtendedBolus pumpId(long pumpId) {
        this.pumpId = pumpId;
        return this;
    }

    public ExtendedBolus source(int source) {
        this.source = source;
        return this;
    }

    public ExtendedBolus durationInMinutes(int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }

    public ExtendedBolus _id(String _id) {
        this._id = _id;
        return this;
    }

    public boolean isEqual(ExtendedBolus other) {
        if (date != other.date) {
            return false;
        }
        if (durationInMinutes != other.durationInMinutes)
            return false;
        if (insulin != other.insulin)
            return false;
        if (pumpId != other.pumpId)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        return true;
    }

    public void copyFrom(ExtendedBolus t) {
        date = t.date;
        _id = t._id;
        durationInMinutes = t.durationInMinutes;
        insulin = t.insulin;
        pumpId = t.pumpId;
    }

    public static ExtendedBolus createFromJson(JSONObject json) {
        ExtendedBolus extendedBolus = new ExtendedBolus()
                .source(Source.NIGHTSCOUT)
                .date(JsonHelper.safeGetLong(json, "mills"))
                .durationInMinutes(JsonHelper.safeGetInt(json, "duration"))
                .insulin(JsonHelper.safeGetDouble(json, "relative") / 60 * JsonHelper.safeGetInt(json, "duration"))
                ._id(JsonHelper.safeGetString(json, "_id"))
                .pumpId(JsonHelper.safeGetLong(json, "pumpId"));
        return extendedBolus;
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

    public String log() {
        return "ExtendedBolus{" +
                "date= " + date +
                ", date= " + DateUtil.dateAndTimeString(date) +
                ", isValid=" + isValid +
                ", _id= " + _id +
                ", pumpId= " + pumpId +
                ", insulin= " + insulin +
                ", durationInMinutes= " + durationInMinutes +
                "}";
    }

    public double absoluteRate() {
        return Round.roundTo(insulin / durationInMinutes * 60, 0.01);
    }

    public double insulinSoFar() {
        return absoluteRate() * getRealDuration() / 60d;
    }

    public IobTotal iobCalc(long time) {
        IobTotal result = new IobTotal(time);
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();

        double realDuration = getDurationToTime(time);

        if (realDuration > 0) {
            double dia_ago = time - dia * 60 * 60 * 1000;
            int aboutFiveMinIntervals = (int) Math.ceil(realDuration / 5d);
            double spacing = realDuration / aboutFiveMinIntervals;

            for (long j = 0L; j < aboutFiveMinIntervals; j++) {
                // find middle of the interval
                long calcdate = (long) (date + j * spacing * 60 * 1000 + 0.5d * spacing * 60 * 1000);

                if (calcdate > dia_ago && calcdate <= time) {
                    double tempBolusSize = absoluteRate() * spacing / 60d;

                    Treatment tempBolusPart = new Treatment();
                    tempBolusPart.insulin = tempBolusSize;
                    tempBolusPart.date = calcdate;

                    Iob aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia);
                    result.iob += aIOB.iobContrib;
                    result.activity += aIOB.activityContrib;
                    result.extendedBolusInsulin += tempBolusPart.insulin;
                }
            }
        }
        return result;
    }

    public IobTotal iobCalc(long time, Profile profile, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        IobTotal result = new IobTotal(time);
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();

        double realDuration = getDurationToTime(time);
        double netBasalAmount = 0d;

        double sensitivityRatio = lastAutosensResult.ratio;
        double normalTarget = 100;

        if (exercise_mode && isTempTarget && profile.getTargetMgdl() >= normalTarget + 5) {
            // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
            // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
            double c = half_basal_exercise_target - normalTarget;
            sensitivityRatio = c / (c + profile.getTargetMgdl() - normalTarget);
        }

        if (realDuration > 0) {
            double netBasalRate;
            double dia_ago = time - dia * 60 * 60 * 1000;
            int aboutFiveMinIntervals = (int) Math.ceil(realDuration / 5d);
            double spacing = realDuration / aboutFiveMinIntervals;

            for (long j = 0L; j < aboutFiveMinIntervals; j++) {
                // find middle of the interval
                long calcdate = (long) (date + j * spacing * 60 * 1000 + 0.5d * spacing * 60 * 1000);

                double basalRate = profile.getBasal(calcdate);
                double basalRateCorrection = basalRate * (sensitivityRatio - 1);


                netBasalRate = absoluteRate() - basalRateCorrection;

                if (calcdate > dia_ago && calcdate <= time) {
                    double tempBolusSize = netBasalRate * spacing / 60d;

                    Treatment tempBolusPart = new Treatment();
                    tempBolusPart.insulin = tempBolusSize;
                    tempBolusPart.date = calcdate;

                    Iob aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia);
                    result.iob += aIOB.iobContrib;
                    result.activity += aIOB.activityContrib;
                    result.extendedBolusInsulin += tempBolusPart.insulin;
                }
            }
        }
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

    public String toString() {
        return "E " + DecimalFormatter.to2Decimal(absoluteRate()) + "U/h @" +
                DateUtil.timeString(date) +
                " " + getRealDuration() + "/" + durationInMinutes + "min";
    }

    public String toStringShort() {
        return "E " + DecimalFormatter.to2Decimal(absoluteRate()) + "U/h ";
    }

    public String toStringMedium() {
        return DecimalFormatter.to2Decimal(absoluteRate()) + "U/h "
                + getRealDuration() + "/" + durationInMinutes + "'";
    }

    public String toStringTotal() {
        return DecimalFormatter.to2Decimal(insulin) + "U ( " +
                DecimalFormatter.to2Decimal(absoluteRate()) + " U/h )";
    }

    // -------- DataPointWithLabelInterface --------
    @Override
    public double getX() {
        return date;
    }

    // default when no sgv around available
    private double yValue = 0;


    @Override
    public double getY() {
        return yValue;
    }

    @Override
    public void setY(double y) {
        yValue = y;
    }

    @Override
    public String getLabel() {
        return toStringTotal();
    }

    @Override
    public long getDuration() {
        return durationInMinutes * 60 * 1000L;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        return PointsWithLabelGraphSeries.Shape.EXTENDEDBOLUS;
    }

    @Override
    public float getSize() {
        return 10;
    }

    @Override
    public int getColor() {
        return Color.CYAN;
    }

}
