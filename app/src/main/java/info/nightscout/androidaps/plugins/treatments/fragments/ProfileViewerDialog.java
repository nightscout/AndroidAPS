package info.nightscout.androidaps.plugins.treatments.fragments;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;

/**
 * Created by adrian on 17/08/17.
 */

public class ProfileViewerDialog extends DialogFragment {

    private long time;

    @BindView(R.id.profileview_noprofile)
    TextView noProfile;
    @BindView(R.id.profileview_invalidprofile)
    TextView invalidProfile;
    @BindView(R.id.profileview_units)
    TextView units;
    @BindView(R.id.profileview_dia)
    TextView dia;
    @BindView(R.id.profileview_activeprofile)
    TextView activeProfile;
    @BindView(R.id.profileview_ic)
    TextView ic;
    @BindView(R.id.profileview_isf)
    TextView isf;
    @BindView(R.id.profileview_basal)
    TextView basal;
    @BindView(R.id.profileview_target)
    TextView target;
    @BindView(R.id.profileview_datedelimiter)
    View dateDelimiter;
    @BindView(R.id.profileview_datelayout)
    LinearLayout dateLayout;
    @BindView(R.id.profileview_date)
    TextView dateTextView;
    @BindView(R.id.profileview_reload)
    Button refreshButton;
    @BindView(R.id.basal_graph)
    ProfileGraph basalGraph;

    private Unbinder unbinder;

    public static ProfileViewerDialog newInstance(long time) {
        ProfileViewerDialog dialog = new ProfileViewerDialog();

        Bundle args = new Bundle();
        args.putLong("time", time);
        dialog.setArguments(args);

        return dialog;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        time = getArguments().getLong("time");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.profileviewer_fragment, container, false);

        unbinder = ButterKnife.bind(this, view);

        refreshButton.setVisibility(View.GONE);
        dateDelimiter.setVisibility(View.VISIBLE);
        dateLayout.setVisibility(View.VISIBLE);

        setContent();
        return view;
    }

    @Override
    public void onResume() {
        ViewGroup.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        getDialog().getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
        super.onResume();
    }

    private void setContent() {
        Profile profile = null;
        ProfileSwitch profileSwitch = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(time);
        if (profileSwitch != null && profileSwitch.profileJson != null) {
            profile = profileSwitch.getProfileObject();
        }
        if (profile != null) {
            noProfile.setVisibility(View.GONE);
            units.setText(profile.getUnits());
            dia.setText(DecimalFormatter.to2Decimal(profile.getDia()) + " h");
            activeProfile.setText(profileSwitch.getCustomizedName());
            dateTextView.setText(DateUtil.dateAndTimeString(profileSwitch.date));
            ic.setText(profile.getIcList());
            isf.setText(profile.getIsfList());
            basal.setText(profile.getBasalList());
            target.setText(profile.getTargetList());
            basalGraph.show(profile);

            if (profile.isValid("ProfileViewDialog"))
                invalidProfile.setVisibility(View.GONE);
            else
                invalidProfile.setVisibility(View.VISIBLE);
        } else {
            noProfile.setVisibility(View.VISIBLE);
        }
    }
}
