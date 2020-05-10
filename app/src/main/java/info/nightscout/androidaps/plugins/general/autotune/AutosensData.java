package info.nightscout.androidaps.plugins.general.autotune;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 25.04.2017.
 */

public class AutosensData {
    private static Logger log = LoggerFactory.getLogger(AutosensData.class);
    @Inject SensitivityAAPSPlugin sensitivityAAPSPlugin;
    @Inject SensitivityWeightedAveragePlugin sensitivityWeightedAveragePlugin;
    @Inject ProfileFunction profileFunction;
    @Inject SP sp;

    class CarbsInPast {
        long time = 0L;
        double carbs = 0d;
        double min5minCarbImpact = 0d;
        double remaining = 0d;

        public CarbsInPast(Treatment t) {
            time = t.date;
            carbs = t.carbs;
            remaining = t.carbs;
            if (sensitivityAAPSPlugin.isEnabled(PluginType.SENSITIVITY) || sensitivityWeightedAveragePlugin.isEnabled(PluginType.SENSITIVITY)) {
                double maxAbsorptionHours = sp.getDouble(R.string.key_absorption_maxtime, 4d);
//                Profile profile = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile(t.date);
                Profile profile = profileFunction.getProfile(time);
                double sens = Profile.toMgdl(profile.getIsfMgdl(t.date), profile.getUnits());
                double ic = profile.getIc(t.date);
                min5minCarbImpact = t.carbs / (maxAbsorptionHours * 60 / 5) * sens / ic;
                //log.debug("Min 5m carbs impact for " + carbs + "g @" + new Date(t.date).toLocaleString() + " for " + maxAbsorptionHours + "h calculated to " + min5minCarbImpact + " ISF: " + sens + " IC: " + ic);
            } else {
                min5minCarbImpact = sp.getDouble("openapsama_min_5m_carbimpact", 3.0);
            }
        }
    }

    public long time = 0L;
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

    public double autosensRatio = 1d;

    public String log(long time) {
        return "AutosensData: " + new Date(time).toLocaleString() + " " + pastSensitivity + " Delta=" + delta + " Bgi=" + bgi + " Deviation=" + deviation + " Absorbed=" + absorbed + " CarbsFromBolus=" + carbsFromBolus + " COB=" + cob + " autosensRatio=" + autosensRatio;
    }

    public int minOld() {
        return (int) ((System.currentTimeMillis() - time) / 1000 / 60);
    }

    // remove carbs older than 4h
    public void removeOldCarbs(long toTime) {
        for (int i = 0; i < activeCarbsList.size(); i++) {
            CarbsInPast c = activeCarbsList.get(i);
            if (c.time + 4 * 60 * 60 * 1000L < toTime) {
                activeCarbsList.remove(i--);
                if (c.remaining > 0)
                    cob -= c.remaining;
                log.debug("Removing carbs at "+ new Date(toTime).toLocaleString() + " + after 4h :" + new Date(c.time).toLocaleString());
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

}
