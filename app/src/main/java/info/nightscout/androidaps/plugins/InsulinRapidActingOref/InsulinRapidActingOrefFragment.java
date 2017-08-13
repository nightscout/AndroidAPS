package info.nightscout.androidaps.plugins.InsulinRapidActingOref;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.InsulinFastacting.ActivityGraph;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinRapidActingOrefFragment extends Fragment {
    static InsulinRapidActingOrefPlugin insulinRapidActingOrefPlugin = new InsulinRapidActingOrefPlugin();

    static public InsulinRapidActingOrefPlugin getPlugin() {
        return insulinRapidActingOrefPlugin;
    }

    TextView insulinName;
    TextView insulinComment;
    TextView insulinDia;
    ActivityGraph insulinGraph;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.insulin_fragment, container, false);

        insulinName = (TextView) view.findViewById(R.id.insulin_name);
        insulinComment = (TextView) view.findViewById(R.id.insulin_comment);
        insulinDia = (TextView) view.findViewById(R.id.insulin_dia);
        insulinGraph = (ActivityGraph) view.findViewById(R.id.insuling_graph);

        updateGUI();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGUI();
    }

    private void updateGUI() {
        insulinName.setText(insulinRapidActingOrefPlugin.getFriendlyName());
        insulinComment.setText(insulinRapidActingOrefPlugin.getComment());
        insulinDia.setText(MainApp.sResources.getText(R.string.dia) + "  " + new Double(insulinRapidActingOrefPlugin.getDia()).toString() + "h");
        insulinGraph.show(insulinRapidActingOrefPlugin);
    }

}
