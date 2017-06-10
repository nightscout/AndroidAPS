package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.support.v4.app.Fragment;

/**
 * Created by adrian on 17/11/16.
 */

public class IobCobCalculatorFragment extends Fragment {

    private static IobCobCalculatorPlugin iobCobCalculatorPlugin = new IobCobCalculatorPlugin();

    public static IobCobCalculatorPlugin getPlugin() {
        return iobCobCalculatorPlugin;
    }
}
