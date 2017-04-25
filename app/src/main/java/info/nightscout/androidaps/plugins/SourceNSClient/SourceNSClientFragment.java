package info.nightscout.androidaps.plugins.SourceNSClient;


import android.support.v4.app.Fragment;

public class SourceNSClientFragment extends Fragment {

    private static SourceNSClientPlugin sourceNSClientPlugin = new SourceNSClientPlugin();

    public static SourceNSClientPlugin getPlugin() {
        return sourceNSClientPlugin;
    }

}
