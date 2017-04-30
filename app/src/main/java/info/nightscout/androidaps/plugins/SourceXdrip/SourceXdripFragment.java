package info.nightscout.androidaps.plugins.SourceXdrip;


import android.support.v4.app.Fragment;

public class SourceXdripFragment extends Fragment {

    private static SourceXdripPlugin sourceXdripPlugin = new SourceXdripPlugin();

    public static SourceXdripPlugin getPlugin() {
        return sourceXdripPlugin;
    }
}
