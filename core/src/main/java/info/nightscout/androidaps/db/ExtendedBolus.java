package info.nightscout.androidaps.db;

/**
 * Created by mike on 21.05.2017.
 */

import android.content.Context;
import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONObject;

import java.util.Objects;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.Insulin;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by mike on 21.05.2017.
 */

@Deprecated
@DatabaseTable(tableName = "ExtendedBoluses")
public class ExtendedBolus implements Interval, DataPointWithLabelInterface {

    @Inject ActivePlugin activePlugin;
    @Inject DateUtil dateUtil;
    @Inject ResourceHelper resourceHelper;

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
    public double insulin = 0d;
    @DatabaseField
    public int durationInMinutes = 0; // duration == 0 means end of extended bolus

    @DatabaseField
    public int insulinInterfaceID = Insulin.InsulinType.OREF_RAPID_ACTING.getValue();

    @DatabaseField
    public double dia = Constants.defaultDIA;

    @Deprecated
    public ExtendedBolus() {
        injector = StaticInjector.Companion.getInstance();
        injector.androidInjector().inject(this);
    }

    public ExtendedBolus(HasAndroidInjector injector) {
        this.injector = injector;
        injector.androidInjector().inject(this);
    }

    public ExtendedBolus(HasAndroidInjector injector, long date) {
        this(injector);
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

    public static ExtendedBolus createFromJson(HasAndroidInjector injector, JSONObject json) {
        ExtendedBolus extendedBolus = new ExtendedBolus(injector)
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
                ", date= " + dateUtil.dateAndTimeString(date) +
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
        return "E " + DecimalFormatter.INSTANCE.to2Decimal(absoluteRate()) + "U/h @" +
                dateUtil.timeString(date) +
                " " + getRealDuration() + "/" + durationInMinutes + "min";
    }

    public String toStringMedium() {
        return DecimalFormatter.INSTANCE.to2Decimal(absoluteRate()) + "U/h "
                + getRealDuration() + "/" + durationInMinutes + "'";
    }

    public String toStringTotal() {
        return DecimalFormatter.INSTANCE.to2Decimal(insulin) + "U ( " +
                DecimalFormatter.INSTANCE.to2Decimal(absoluteRate()) + " U/h )";
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
    public int getColor(Context context) {
        return resourceHelper.getAttributeColor(context, R.attr.extendedBolus);
    }

}
