package info.nightscout.androidaps.plugins.InsulinFastactingProlonged;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFastactingProlongedPlugin implements PluginBase, InsulinInterface {

    private static boolean fragmentEnabled = false;
    private static boolean fragmentVisible = false;

    @Override
    public int getType() {
        return INSULIN;
    }

    @Override
    public String getFragmentClass() {
        return InsulinFastactingProlongedFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.fastactinginsulinprolonged);
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

    // Insulin interface
    @Override
    public int getId() {
        return FASTACTINGINSULINPROLONGED;
    }

    @Override
    public String getFriendlyName() {
        return MainApp.sResources.getString(R.string.fastactinginsulinprolonged);
    }

    @Override
    public String getComment() {
        return MainApp.sResources.getString(R.string.fastactinginsulincomment);
    }

    @Override
    public double getDia() {
        ProfileInterface profileInterface =  MainApp.getConfigBuilder().getActiveProfile();
        if (profileInterface.getProfile() != null)
            return profileInterface.getProfile().getDia();
        return Constants.defaultDIA;
    }

    @Override
    public Iob iobCalc(Treatment treatment, long time, Double dia) {
        Iob result = new Iob();

        //Double scaleFactor = 3.0 / dia;
        Double peak = 75d * dia / 6.0;
        Double tail = 180d * dia / 6.0;
        Double end = 360d * dia / 6.0;
        Double Total =  2 * peak + (tail - peak) * 5 / 2 + (end - tail) / 2;

        if (treatment.insulin != 0d) {
            Long bolusTime = treatment.created_at.getTime();
            Double minAgo = (time - bolusTime) / 1000d / 60d;

            if (minAgo < peak) {
                Double x1 = 6 / dia * minAgo / 5d + 1;
                result.iobContrib = treatment.insulin * (1 - 0.0012595 * x1 * x1 + 0.0012595 * x1);
                // units: BG (mg/dL)  = (BG/U) *    U insulin     * scalar
                result.activityContrib = treatment.insulin * ((2 * peak / Total) * 2 / peak / peak * minAgo);
            } else if (minAgo < tail) {
                Double x2 = (6 / dia * (minAgo - peak)) / 5;
                result.iobContrib = treatment.insulin * (0.00074 * x2 * x2 - 0.0403 * x2 + 0.69772);
                result.activityContrib = treatment.insulin * (-((2 * peak / Total) * 2 / peak * 3 / 4) / (tail - peak) * (minAgo - peak) + (2 * peak / Total) * 2 / peak);
            } else if (minAgo < end) {
                Double x3 = (6 / dia * (minAgo - tail)) / 5;
                result.iobContrib = treatment.insulin * (0.0001323 * x3 * x3 - 0.0097 * x3 + 0.17776);
                result.activityContrib = treatment.insulin * (-((2 * peak / Total) * 2 / peak * 1 / 4) / (end - tail) * (minAgo - tail) + (2 * peak / Total) * 2 / peak / 4);
            }

        }
        return result;
    }
}
