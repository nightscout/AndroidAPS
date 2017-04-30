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
    int CONSTRAINTS = 7;
    int LOOP = 8;
    int BGSOURCE = 9;
    int INSULIN = 10;
    int LAST = 11; // keep always highest number

    int getType();
    String getFragmentClass();

    String getName();
    String getNameShort();
    boolean isEnabled(int type);
    boolean isVisibleInTabs(int type);
    boolean canBeHidden(int type);
    boolean hasFragment();
    boolean showInList(int type);
    void setFragmentEnabled(int type, boolean fragmentEnabled);
    void setFragmentVisible(int type, boolean fragmentVisible);
}