package info.nightscout.androidaps.plugins.SourceXdrip;


import android.support.v4.app.Fragment;

import info.nightscout.androidaps.interfaces.FragmentBase;

public class SourceXdripFragment extends Fragment implements FragmentBase {

    private static SourceXdripPlugin sourceXdripPlugin = new SourceXdripPlugin();

    public static SourceXdripPlugin getPlugin() {
        return sourceXdripPlugin;
    }
}
