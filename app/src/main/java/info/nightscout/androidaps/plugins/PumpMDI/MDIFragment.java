package info.nightscout.androidaps.plugins.PumpMDI;


import android.support.v4.app.Fragment;

public class MDIFragment extends Fragment {
    private static MDIPlugin mdiPlugin = new MDIPlugin();

    public static MDIPlugin getPlugin() {
        return mdiPlugin;
    }
}
