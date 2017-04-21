package info.nightscout.androidaps.plugins.InsulinFastacting;

import android.support.v4.app.Fragment;

import info.nightscout.androidaps.interfaces.FragmentBase;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFastactingFragment extends Fragment implements FragmentBase {
    static InsulinFastactingPlugin insulinFastactingPlugin = new InsulinFastactingPlugin();

    static public InsulinFastactingPlugin getPlugin() {
        return insulinFastactingPlugin;
    }


}
