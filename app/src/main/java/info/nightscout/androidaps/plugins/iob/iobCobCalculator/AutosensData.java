package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 25.04.2017.
 */

public class AutosensData implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(L.AUTOSENS);

    public void setChartTime(long chartTime) {
        this.chartTime = chartTime;
    }

    static class CarbsInPast {
        long time = 0L;
        double carbs = 0d;
        double min5minCarbImpact = 0d;
        double remaining = 0d;

        CarbsInPast(Treatment t) {
            time = t.date;
            carbs = t.carbs;
            remaining = t.carbs;
            if (SensitivityAAPSPlugin.getPlugin().isEnabled(PluginType.SENSITIVITY) || SensitivityWeightedAveragePlugin.getPlugin().isEnabled(PluginType.SENSITIVITY)) {
                double maxAbsorptionHours = SP.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
                Profile profile = ProfileFunctions.getInstance().getProfile(t.date);
                double sens = Profile.toMgdl(profile.getIsf(t.date), profile.getUnits());
                double ic = profile.getIc(t.date);
                min5minCarbImpact = t.carbs / (maxAbsorptionHours * 60 / 5) * sens / ic;
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Min 5m carbs impact for " + carbs + "g @" + new Date(t.date).toLocaleString() + " for " + maxAbsorptionHours + "h calculated to " + min5minCarbImpact + " ISF: " + sens + " IC: " + ic);
            } else {
                min5minCarbImpact = SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact);
            }
        }

       CarbsInPast (CarbsInPast other) {
            this.time = other.time;
            this.carbs = other.carbs;
            this.min5minCarbImpact = other.min5minCarbImpact;
            this.remaining = other.remaining;
        }

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "CarbsInPast: time: %s carbs: %.02f min5minCI: %.02f remaining: %.2f", new Date(time).toLocaleString(), carbs, min5minCarbImpact, remaining);
        }
    }

    public long time = 0L;
    public double bg = 0; // mgdl
    private long chartTime;
    public String pastSensitivity = "";
    public double deviation = 0d;
    public boolean validDeviation = false;
    List<CarbsInPast> activeCarbsList = new ArrayList<>();
    double absorbed = 0d;
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

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "AutosensData: %s pastSensitivity=%s  delta=%.02f  avgDelta=%.02f bgi=%.02f deviation=%.02f avgDeviation=%.02f absorbed=%.02f carbsFromBolus=%.02f cob=%.02f autosensRatio=%.02f slopeFromMaxDeviation=%.02f slopeFromMinDeviation=%.02f activeCarbsList=%s",
                new Date(time).toLocaleString(), pastSensitivity, delta, avgDelta, bgi, deviation, avgDeviation, absorbed, carbsFromBolus, cob, autosensResult.ratio, slopeFromMaxDeviation, slopeFromMinDeviation, activeCarbsList.toString());
    }

    public List<CarbsInPast> cloneCarbsList() {
        List<CarbsInPast> newActiveCarbsList = new ArrayList<>();

        for(CarbsInPast c: activeCarbsList) {
            newActiveCarbsList.add(new CarbsInPast(c));
        }

        return newActiveCarbsList;
    }

    // remove carbs older than timeframe
    public void removeOldCarbs(long toTime) {
        double maxAbsorptionHours = Constants.DEFAULT_MAX_ABSORPTION_TIME;
        if (SensitivityAAPSPlugin.getPlugin().isEnabled(PluginType.SENSITIVITY) || SensitivityWeightedAveragePlugin.getPlugin().isEnabled(PluginType.SENSITIVITY)) {
            maxAbsorptionHours = SP.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        } else {
            maxAbsorptionHours = SP.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        }
        for (int i = 0; i < activeCarbsList.size(); i++) {
            CarbsInPast c = activeCarbsList.get(i);
            if (c.time + maxAbsorptionHours * 60 * 60 * 1000L < toTime) {
                activeCarbsList.remove(i--);
                if (c.remaining > 0)
                    cob -= c.remaining;
                if (L.isEnabled(L.AUTOSENS))
                    log.debug("Removing carbs at " + new Date(toTime).toLocaleString() + " after " + maxAbsorptionHours + "h > " + c.toString());
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
        return MainApp.gc(R.color.cob);
    }

}
