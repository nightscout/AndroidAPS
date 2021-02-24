package info.nightscout.androidaps.plugins.iob.iobCobCalculator.data;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class AutosensData implements DataPointWithLabelInterface {

    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject ProfileFunction profileFunction;
    @Inject DateUtil dateUtil;

    public AutosensData(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public void setChartTime(long chartTime) {
        this.chartTime = chartTime;
    }

    public class CarbsInPast {
        long time;
        double carbs;
        public double min5minCarbImpact;
        double remaining;

        public CarbsInPast(Treatment t, boolean isAAPSOrWeighted) {
            time = t.date;
            carbs = t.carbs;
            remaining = t.carbs;
            if (isAAPSOrWeighted) {
                double maxAbsorptionHours = sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
                Profile profile = profileFunction.getProfile(t.date);
                double sens = profile.getIsfMgdl(t.date);
                double ic = profile.getIc(t.date);
                min5minCarbImpact = t.carbs / (maxAbsorptionHours * 60 / 5) * sens / ic;
                aapsLogger.debug(LTag.AUTOSENS, "Min 5m carbs impact for " + carbs + "g @" + dateUtil.dateAndTimeString(t.date) + " for " + maxAbsorptionHours + "h calculated to " + min5minCarbImpact + " ISF: " + sens + " IC: " + ic);
            } else {
                min5minCarbImpact = sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact);
            }
        }

        CarbsInPast(CarbsInPast other) {
            this.time = other.time;
            this.carbs = other.carbs;
            this.min5minCarbImpact = other.min5minCarbImpact;
            this.remaining = other.remaining;
        }

        @NonNull @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "CarbsInPast: time: %s carbs: %.02f min5minCI: %.02f remaining: %.2f", dateUtil.dateAndTimeString(time), carbs, min5minCarbImpact, remaining);
        }
    }

    public long time = 0L;
    public double bg = 0; // mgdl
    private long chartTime;
    public String pastSensitivity = "";
    public double deviation = 0d;
    public boolean validDeviation = false;
    public List<CarbsInPast> activeCarbsList = new ArrayList<>();
    public double absorbed = 0d;
    public double carbsFromBolus = 0d;
    public double cob = 0;
    public double bgi = 0d;
    public double delta = 0d;
    public double avgDelta = 0d;
    public double avgDeviation = 0d;

    public AutosensResult autosensResult = new AutosensResult();
    public double slopeFromMaxDeviation = 0;
    public double slopeFromMinDeviation = 999;
    public double usedMinCarbsImpact = 0d;
    public boolean failoverToMinAbsorbtionRate = false;

    // Oref1
    public boolean absorbing = false;
    public double mealCarbs = 0;
    public int mealStartCounter = 999;
    public String type = "";
    public boolean uam = false;
    public List<Double> extraDeviation = new ArrayList<>();

    @NonNull @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "AutosensData: %s pastSensitivity=%s  delta=%.02f  avgDelta=%.02f bgi=%.02f deviation=%.02f avgDeviation=%.02f absorbed=%.02f carbsFromBolus=%.02f cob=%.02f autosensRatio=%.02f slopeFromMaxDeviation=%.02f slopeFromMinDeviation=%.02f activeCarbsList=%s",
                dateUtil.dateAndTimeString(time), pastSensitivity, delta, avgDelta, bgi, deviation, avgDeviation, absorbed, carbsFromBolus, cob, autosensResult.ratio, slopeFromMaxDeviation, slopeFromMinDeviation, activeCarbsList.toString());
    }

    public List<CarbsInPast> cloneCarbsList() {
        List<CarbsInPast> newActiveCarbsList = new ArrayList<>();

        for (CarbsInPast c : activeCarbsList) {
            newActiveCarbsList.add(new CarbsInPast(c));
        }

        return newActiveCarbsList;
    }

    // remove carbs older than timeframe
    public void removeOldCarbs(long toTime, boolean isAAPSOrWeighted) {
        double maxAbsorptionHours;
        if (isAAPSOrWeighted) {
            maxAbsorptionHours = sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        } else {
            maxAbsorptionHours = sp.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        }
        for (int i = 0; i < activeCarbsList.size(); i++) {
            CarbsInPast c = activeCarbsList.get(i);
            if (c.time + maxAbsorptionHours * 60 * 60 * 1000L < toTime) {
                activeCarbsList.remove(i--);
                if (c.remaining > 0)
                    cob -= c.remaining;
                aapsLogger.debug(LTag.AUTOSENS, "Removing carbs at " + dateUtil.dateAndTimeString(toTime) + " after " + maxAbsorptionHours + "h > " + c.toString());
            }
        }
    }

    public void substractAbosorbedCarbs() {
        double ac = absorbed;
        for (int i = 0; i < activeCarbsList.size() && ac > 0; i++) {
            CarbsInPast c = activeCarbsList.get(i);
            if (c.remaining > 0) {
                double sub = Math.min(ac, c.remaining);
                c.remaining -= sub;
                ac -= sub;
            }
        }
    }

    // ------- DataPointWithLabelInterface ------

    private Scale scale;

    public void setScale(Scale scale) {
        this.scale = scale;
    }

    @Override
    public double getX() {
        return chartTime;
    }

    @Override
    public double getY() {
        return scale.transform(cob);
    }

    @Override
    public void setY(double y) {

    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public PointsWithLabelGraphSeries.Shape getShape() {
        return PointsWithLabelGraphSeries.Shape.COBFAILOVER;
    }

    @Override
    public float getSize() {
        return 0.5f;
    }

    @Override
    public int getColor() {
        return resourceHelper.gc(R.color.cob);
    }

}
