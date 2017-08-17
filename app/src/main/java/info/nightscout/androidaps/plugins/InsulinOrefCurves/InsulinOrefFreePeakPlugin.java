package info.nightscout.androidaps.plugins.InsulinOrefCurves;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.SP;

/**
 * Created by adrian on 14/08/17.
 */

public class InsulinOrefFreePeakPlugin extends InsulinOrefBasePlugin {

    private static boolean fragmentEnabled = false;
    private static boolean fragmentVisible = false;

    public static final int DEFAULT_PEAK = 75;

    @Override
    public int getId() {
        return OREF_FREE_PEAK;
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.free_peak_oref);
    }

    @Override
    public String getFragmentClass() {
        return InsulinOrefFreePeakFragment.class.getName();
    }

    @Override
    public String getFriendlyName() {
        return MainApp.sResources.getString(R.string.free_peak_oref);
    }

    @Override
    public String commentStandardText() {
        return MainApp.sResources.getString(R.string.insulin_peak_time) + ": " + getPeak();
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == INSULIN) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == INSULIN) this.fragmentVisible = fragmentVisible;
    }

    @Override
    int getPeak() {
        return SP.getInt(R.string.key_insulin_oref_peak, DEFAULT_PEAK);
    }
}
