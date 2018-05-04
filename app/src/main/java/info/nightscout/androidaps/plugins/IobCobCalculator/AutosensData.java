package info.nightscout.androidaps.plugins.IobCobCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.OpenAPSSMB.SMBDefaults;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.Scale;
import info.nightscout.androidaps.plugins.SensitivityAAPS.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.SensitivityWeightedAverage.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.utils.SP;

/**
 * Created by mike on 25.04.2017.
 */

public class AutosensData implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(AutosensData.class);

    public void setChartTime(long chartTime) {
        this.chartTime = chartTime;
    }

    static class CarbsInPast {
        long time = 0L;
        double carbs = 0d;
        double min5minCarbImpact = 0d;
        double remaining = 0d;

        public CarbsInPast(Treatment t) {
            time = t.date;
            carbs = t.carbs;
            remaining = t.carbs;
            if (SensitivityAAPSPlugin.getPlugin().isEnabled(PluginType.SENSITIVITY) || SensitivityWeightedAveragePlugin.getPlugin().isEnabled(PluginType.SENSITIVITY)) {
                double maxAbsorptionHours = SP.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
                Profile profile = MainApp.getConfigBuilder().getProfile(t.date);
                double sens = Profile.toMgdl(profile.getIsf(t.date), profile.getUnits());
                double ic = profile.getIc(t.date);
                min5minCarbImpact = t.carbs / (maxAbsorptionHours * 60 / 5) * sens / ic;
                log.debug("Min 5m carbs impact for " + carbs + "g @" + new Date(t.date).toLocaleString() + " for " + maxAbsorptionHours + "h calculated to " + min5minCarbImpact + " ISF: " + sens + " IC: " + ic);
            } else {
                min5minCarbImpact = SP.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact);
            }
        }
    }

    public long time = 0L;
    long chartTime;
    public String pastSensitivity = "";
    public double deviation = 0d;
    boolean nonCarbsDeviation = false;
    public boolean nonEqualDeviation = false;
    List<CarbsInPast> activeCarbsList = new ArrayList<>();
    double absorbed = 0d;
    public double carbsFromBolus = 0d;
    public double cob = 0;
    public double bgi = 0d;
    public double delta = 0d;
    public double avgDelta = 0d;
    public double avgDeviation = 0d;

    public double autosensRatio = 1d;
    public double slopeFromMaxDeviation = 0;
    public double slopeFromMinDeviation = 999;
    public double usedMinCarbsImpact = 0d;
    public boolean failoverToMinAbsorbtionRate = false;

    @Override
    public String toString() {
        return "AutosensData: " + new Date(time).toLocaleString() + " " + pastSensitivity + " Delta=" + delta + " avgDelta=" + avgDelta + " Bgi=" + bgi + " Deviation=" + deviation + " avgDeviation=" + avgDeviation + " Absorbed=" + absorbed + " CarbsFromBolus=" + carbsFromBolus + " COB=" + cob + " autosensRatio=" + autosensRatio + " slopeFromMaxDeviation=" + slopeFromMaxDeviation + " slopeFromMinDeviation =" + slopeFromMinDeviation;
    }

    public int minOld() {
        return (int) ((System.currentTimeMillis() - time) / 1000 / 60);
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
                log.debug("Removing carbs at " + new Date(toTime).toLocaleString() + " + after " + maxAbsorptionHours + "h :" + new Date(c.time).toLocaleString());
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
