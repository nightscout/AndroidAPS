package info.nightscout.androidaps.plugins.Insulin;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFastactingPlugin implements PluginBase, InsulinInterface {

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = false;

    private static InsulinFastactingPlugin plugin = null;

    public static InsulinFastactingPlugin getPlugin() {
        if (plugin == null)
            plugin = new InsulinFastactingPlugin();
        return plugin;
    }

    @Override
    public int getType() {
        return INSULIN;
    }

    @Override
    public String getFragmentClass() {
        return InsulinFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.fastactinginsulin);
    }

    @Override
    public String getNameShort() {
        return MainApp.sResources.getString(R.string.insulin_shortname);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == INSULIN && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == INSULIN && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == INSULIN) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == INSULIN) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return -1;
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
