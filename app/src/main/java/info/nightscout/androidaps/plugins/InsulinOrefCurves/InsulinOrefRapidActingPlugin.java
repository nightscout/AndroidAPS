package info.nightscout.androidaps.plugins.InsulinOrefCurves;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by adrian on 14/08/17.
 */

public class InsulinOrefRapidActingPlugin extends InsulinOrefBasePlugin {

    private static boolean fragmentEnabled = false;
    private static boolean fragmentVisible = false;

    public static final int PEAK = 75;

    @Override
    public int getId() {
        return OREF_RAPID_ACTING;
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.rapid_acting_oref);
    }

    @Override
    public String getFragmentClass() {
        return InsulinOrefRapidActingFragment.class.getName();
    }

    @Override
    public String getFriendlyName() {
        return MainApp.sResources.getString(R.string.rapid_acting_oref);
    }

    @Override
    public String commentStandardText() {
        return MainApp.sResources.getString(R.string.fastactinginsulincomment);
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
        return PEAK;
    }
}
