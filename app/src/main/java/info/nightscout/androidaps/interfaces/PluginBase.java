package info.nightscout.androidaps.interfaces;

import java.util.Date;

/**
 * Created by mike on 09.06.2016.
 */
public interface PluginBase {
    int GENERAL = 1;
    int TREATMENT = 2;
    int TEMPBASAL = 3;
    int PROFILE = 4;
    int APS = 5;
    int PUMP = 6;
    int CONSTRAINS = 7;
    int LOOP = 8;

    public int getType();

    String getName();
    boolean isEnabled();
    boolean isVisibleInTabs();
    boolean canBeHidden();
    void setFragmentEnabled(boolean fragmentEnabled);
    void setFragmentVisible(boolean fragmentVisible);
}