package info.nightscout.androidaps.plugins;

/**
 * Created by mike on 09.06.2016.
 */
public interface PluginBase {
    int GENERAL = 1;
    int PROFILE = 2;
    int APS = 3;
    int PUMP = 4;

    public int getType();
    public boolean isFragmentVisible();
}
