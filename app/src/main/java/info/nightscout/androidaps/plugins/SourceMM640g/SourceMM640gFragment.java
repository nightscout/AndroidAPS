package info.nightscout.androidaps.plugins.SourceMM640g;


import android.support.v4.app.Fragment;

import info.nightscout.androidaps.interfaces.FragmentBase;

public class SourceMM640gFragment extends Fragment implements FragmentBase {

    private static SourceMM640gPlugin sourceMM640gPlugin = new SourceMM640gPlugin();

    public static SourceMM640gPlugin getPlugin() {
        return sourceMM640gPlugin;
    }

}
