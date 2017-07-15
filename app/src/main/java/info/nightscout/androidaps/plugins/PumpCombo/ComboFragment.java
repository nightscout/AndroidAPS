package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import de.jotomo.ruffyscripter.commands.PumpState;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;

public class ComboFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(ComboFragment.class);

    private static ComboPlugin comboPlugin = new ComboPlugin();

    public static ComboPlugin getPlugin() {
        return comboPlugin;
    }

    private TextView statusText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        statusText = (TextView) view.findViewById(R.id.comboStatusEditText);

        updateGUI();
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventComboPumpUpdateGUI ev) {
        updateGUI();
    }

    // TODO *very* quick hack
    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (getPlugin() == null) {
                        statusText.setText("Initializing");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append(getPlugin().statusSummary);
                        PumpState ps = getPlugin().pumpState;
                        if (ps != null) {
                            sb.append("\n\n");
                            sb.append(ps.toString()
                                    // i know ... i need to take a break already
                                    .replaceAll(", ", "\n")
                                    .replaceAll("PumpState\\{", "\n")
                                    .replaceAll("\\}", "\n")
                            );
                        }
                        statusText.setText(sb.toString());
                    }
                }
            });
    }
}
