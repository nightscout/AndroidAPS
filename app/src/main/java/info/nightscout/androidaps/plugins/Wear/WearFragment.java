package info.nightscout.androidaps.plugins.Wear;

import android.content.Context;

import info.nightscout.androidaps.interfaces.FragmentBase;

/**
 * Created by adrian on 17/11/16.
 */

public class WearFragment implements FragmentBase {

    private static WearPlugin wearPlugin;

    public static WearPlugin getPlugin(Context ctx) {

        if (wearPlugin == null){
            wearPlugin = new WearPlugin(ctx);
        }

        return wearPlugin;
    }


}
