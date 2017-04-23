package info.nightscout.androidaps.plugins.ConstraintsSafety;


import android.support.v4.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SafetyFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(SafetyFragment.class);

    private static SafetyPlugin safetyPlugin = new SafetyPlugin();

    public static SafetyPlugin getPlugin() {
        return safetyPlugin;
    }
}
