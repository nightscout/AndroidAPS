package info.nightscout.androidaps.plugins.InsulinOrefCurves;

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
 * Created by adrian on 14/08/17.
 */

public class InsulinOrefFreePeakFragment extends Fragment {

    static InsulinOrefFreePeakPlugin insulinPlugin = new InsulinOrefFreePeakPlugin();

    static public InsulinOrefFreePeakPlugin getPlugin() {
        return insulinPlugin;
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
        insulinName.setText(insulinPlugin.getFriendlyName());
        insulinComment.setText(insulinPlugin.getComment());
        insulinDia.setText(MainApp.sResources.getText(R.string.dia) + "  " + Double.toString(insulinPlugin.getDia()) + "h");
        insulinGraph.show(insulinPlugin);
    }

}
