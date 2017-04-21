package info.nightscout.androidaps.plugins.InsulinFastacting;

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

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinFastactingFragment extends Fragment implements FragmentBase {
    static InsulinFastactingPlugin insulinFastactingPlugin = new InsulinFastactingPlugin();

    static public InsulinFastactingPlugin getPlugin() {
        return insulinFastactingPlugin;
    }

    private Unbinder unbinder;
    @BindView(R.id.insulin_name)
    TextView insulinName;
    @BindView(R.id.insulin_comment)
    TextView insulinComment;
    @BindView(R.id.insulin_dia)
    TextView insulinDia;
    @BindView(R.id.insulin_activity)
    ImageView insulinActivity;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.insulin_fragment, container, false);

        unbinder = ButterKnife.bind(this, view);

        insulinName.setText(insulinFastactingPlugin.getFriendlyName());
        insulinComment.setText(insulinFastactingPlugin.getComment());
        insulinDia.setText(MainApp.sResources.getText(R.string.dia) + "  " + new Double(insulinFastactingPlugin.getDia()).toString() + "h");
        insulinActivity.setImageDrawable(MainApp.sResources.getDrawable(insulinFastactingPlugin.getResourcePicture()));

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }


}
