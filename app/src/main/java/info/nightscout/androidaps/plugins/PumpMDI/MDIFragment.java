package info.nightscout.androidaps.plugins.PumpMDI;


import android.support.v4.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.interfaces.FragmentBase;

public class MDIFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(MDIFragment.class);

    private static MDIPlugin mdiPlugin = new MDIPlugin();

    public static MDIPlugin getPlugin() {
        return mdiPlugin;
    }
}
