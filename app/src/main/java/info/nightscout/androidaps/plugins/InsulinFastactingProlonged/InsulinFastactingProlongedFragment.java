package info.nightscout.androidaps.plugins.InsulinFastactingProlonged;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.InsulinFastacting.ActivityGraph;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFastactingProlongedFragment extends Fragment implements FragmentBase {
    static InsulinFastactingProlongedPlugin insulinFastactingProlongedPlugin = new InsulinFastactingProlongedPlugin();

    static public InsulinFastactingProlongedPlugin getPlugin() {
        return insulinFastactingProlongedPlugin;
    }

    private Unbinder unbinder;
    @BindView(R.id.insulin_name)
    TextView insulinName;
    @BindView(R.id.insulin_comment)
    TextView insulinComment;
    @BindView(R.id.insulin_dia)
    TextView insulinDia;
    @BindView(R.id.insuling_graph)
    ActivityGraph insulinGraph;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.insulin_fragment, container, false);

        unbinder = ButterKnife.bind(this, view);

        insulinName.setText(insulinFastactingProlongedPlugin.getFriendlyName());
        insulinComment.setText(insulinFastactingProlongedPlugin.getComment());
        insulinDia.setText(MainApp.sResources.getText(R.string.dia) + "  " + new Double(insulinFastactingProlongedPlugin.getDia()).toString() + "h");
        insulinGraph.show(insulinFastactingProlongedPlugin);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }


}
