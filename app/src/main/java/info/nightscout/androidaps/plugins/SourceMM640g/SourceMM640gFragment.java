package info.nightscout.androidaps.plugins.SourceMM640g;


import android.support.v4.app.Fragment;

public class SourceMM640gFragment extends Fragment {

    private static SourceMM640gPlugin sourceMM640gPlugin = new SourceMM640gPlugin();

    public static SourceMM640gPlugin getPlugin() {
        return sourceMM640gPlugin;
    }

}
