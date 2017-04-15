package info.nightscout.androidaps.plugins.SourceGlimp;


import android.support.v4.app.Fragment;

import info.nightscout.androidaps.interfaces.FragmentBase;

public class SourceGlimpFragment extends Fragment implements FragmentBase {

    private static SourceGlimpPlugin sourceGlimpPlugin = new SourceGlimpPlugin();

    public static SourceGlimpPlugin getPlugin() {
        return sourceGlimpPlugin;
    }

}
