package info.nightscout.androidaps.plugins.SourceGlimp;


import android.support.v4.app.Fragment;

public class SourceGlimpFragment extends Fragment {

    private static SourceGlimpPlugin sourceGlimpPlugin = new SourceGlimpPlugin();

    public static SourceGlimpPlugin getPlugin() {
        return sourceGlimpPlugin;
    }

}
