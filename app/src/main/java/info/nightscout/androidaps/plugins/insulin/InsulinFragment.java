package info.nightscout.androidaps.plugins.insulin;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFragment extends Fragment {
    TextView insulinName;
    TextView insulinComment;
    TextView insulinDia;
    ActivityGraph insulinGraph;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.insulin_fragment, container, false);

            insulinName = (TextView) view.findViewById(R.id.insulin_name);
            insulinComment = (TextView) view.findViewById(R.id.insulin_comment);
            insulinDia = (TextView) view.findViewById(R.id.insulin_dia);
            insulinGraph = (ActivityGraph) view.findViewById(R.id.insuling_graph);

            updateGUI();

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGUI();
    }

    private void updateGUI() {
        insulinName.setText(ConfigBuilderPlugin.getPlugin().getActiveInsulin().getFriendlyName());
        insulinComment.setText(ConfigBuilderPlugin.getPlugin().getActiveInsulin().getComment());
        insulinDia.setText(MainApp.gs(R.string.dia) + "  " + ConfigBuilderPlugin.getPlugin().getActiveInsulin().getDia() + "h");
        insulinGraph.show(ConfigBuilderPlugin.getPlugin().getActiveInsulin());
    }

}
