package info.nightscout.androidaps.plugins.SourceNSClient;


import android.support.v4.app.Fragment;

import info.nightscout.androidaps.interfaces.FragmentBase;

public class SourceNSClientFragment extends Fragment implements FragmentBase {

    private static SourceNSClientPlugin sourceNSClientPlugin = new SourceNSClientPlugin();

    public static SourceNSClientPlugin getPlugin() {
        return sourceNSClientPlugin;
    }

}
