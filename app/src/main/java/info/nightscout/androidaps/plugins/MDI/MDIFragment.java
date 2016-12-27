package info.nightscout.androidaps.plugins.MDI;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.VirtualPump.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.VirtualPump.events.EventVirtualPumpUpdateGui;

public class MDIFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(MDIFragment.class);

    private static MDIPlugin mdiPlugin = new MDIPlugin();

    public static MDIPlugin getPlugin() {
        return mdiPlugin;
    }
}
