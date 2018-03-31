package info.nightscout.androidaps.plugins.Insulin;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFastactingPlugin extends PluginBase implements InsulinInterface {

    private static InsulinFastactingPlugin plugin = null;

    public static InsulinFastactingPlugin getPlugin() {
        if (plugin == null)
            plugin = new InsulinFastactingPlugin();
        return plugin;
    }

    public InsulinFastactingPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.INSULIN)
                .fragmentClass(InsulinFragment.class.getName())
                .pluginName(R.string.fastactinginsulin)
                .shortName(R.string.insulin_shortname)
        );
    }

    // Insulin interface
    @Override
    public int getId() {
        return FASTACTINGINSULIN;
    }

    @Override
    public String getFriendlyName() {
        return MainApp.sResources.getString(R.string.fastactinginsulin);
    }

    @Override
    public String getComment() {
        return MainApp.sResources.getString(R.string.fastactinginsulincomment);
    }

    @Override
    public double getDia() {
        return MainApp.getConfigBuilder().getProfile() != null ? MainApp.getConfigBuilder().getProfile().getDia() : Constants.defaultDIA;
    }

    @Override
    public Iob iobCalcForTreatment(Treatment treatment, long time, double dia) {
        Iob result = new Iob();

        double scaleFactor = 3.0 / dia;
        double peak = 75d;
        double end = 180d;

        if (treatment.insulin != 0d) {
            long bolusTime = treatment.date;
            double minAgo = scaleFactor * (time - bolusTime) / 1000d / 60d;

            if (minAgo < peak) {
                double x1 = minAgo / 5d + 1;
                result.iobContrib = treatment.insulin * (1 - 0.001852 * x1 * x1 + 0.001852 * x1);
                // units: BG (mg/dL)  = (BG/U) *    U insulin     * scalar
                result.activityContrib = treatment.insulin * (2 / dia / 60 / peak) * minAgo;

            } else if (minAgo < end) {
                double x2 = (minAgo - 75) / 5;
                result.iobContrib = treatment.insulin * (0.001323 * x2 * x2 - 0.054233 * x2 + 0.55556);
                result.activityContrib = treatment.insulin * (2 / dia / 60 - (minAgo - peak) * 2 / dia / 60 / (60 * 3 - peak));
            }
        }
        return result;
    }

}
