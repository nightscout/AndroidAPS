package info.nightscout.androidaps.plugins.XDripStatusline;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 17/11/16.
 */

public class StatuslineFragment extends Fragment {

    private static StatuslinePlugin statuslinePlugin;

    public static StatuslinePlugin getPlugin(Context ctx) {

        if (statuslinePlugin == null) {
            statuslinePlugin = new StatuslinePlugin(ctx);
        }

        return statuslinePlugin;
    }

}
