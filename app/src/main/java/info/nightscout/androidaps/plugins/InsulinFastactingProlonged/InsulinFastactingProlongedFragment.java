package info.nightscout.androidaps.plugins.InsulinFastactingProlonged;

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

public class InsulinFastactingProlongedFragment extends Fragment {
    static InsulinFastactingProlongedPlugin insulinFastactingProlongedPlugin = new InsulinFastactingProlongedPlugin();

    static public InsulinFastactingProlongedPlugin getPlugin() {
        return insulinFastactingProlongedPlugin;
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
        insulinName.setText(insulinFastactingProlongedPlugin.getFriendlyName());
        insulinComment.setText(insulinFastactingProlongedPlugin.getComment());
        insulinDia.setText(MainApp.sResources.getText(R.string.dia) + "  " + Double.toString(insulinFastactingProlongedPlugin.getDia()) + "h");
        insulinGraph.show(insulinFastactingProlongedPlugin);
    }

}
