package info.nightscout.androidaps.plugins.SafetyFragment;


import android.support.v4.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.interfaces.FragmentBase;

public class SafetyFragment extends Fragment implements FragmentBase{
    private static Logger log = LoggerFactory.getLogger(SafetyFragment.class);

    private static SafetyPlugin safetyPlugin = new SafetyPlugin();

    public static SafetyPlugin getPlugin() {
        return safetyPlugin;
    }
}
