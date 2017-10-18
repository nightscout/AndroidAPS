package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.plugins.PumpDanaR.Dialogs.ProfileViewDialog;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by adrian on 17/08/17.
 */

public class ProfileViewerDialog extends DialogFragment {

    private long time;

    private static Logger log = LoggerFactory.getLogger(ProfileViewDialog.class);

    private TextView noProfile;
    private TextView units;
    private TextView dia;
    private TextView activeProfile;
    private TextView ic;
    private TextView isf;
    private TextView basal;
    private TextView target;
    private View dateDelimiter;
    private LinearLayout dateLayout;
    private TextView dateTextView;
    private Button refreshButton;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.profileviewer_fragment, container, false);

        noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
        units = (TextView) layout.findViewById(R.id.profileview_units);
        dia = (TextView) layout.findViewById(R.id.profileview_dia);
        activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
        ic = (TextView) layout.findViewById(R.id.profileview_ic);
        isf = (TextView) layout.findViewById(R.id.profileview_isf);
        basal = (TextView) layout.findViewById(R.id.profileview_basal);
        target = (TextView) layout.findViewById(R.id.profileview_target);
        refreshButton = (Button) layout.findViewById(R.id.profileview_reload);
        refreshButton.setVisibility(View.GONE);
        dateDelimiter = layout.findViewById(R.id.profileview_datedelimiter);
        dateDelimiter.setVisibility(View.VISIBLE);
        dateLayout = (LinearLayout) layout.findViewById(R.id.profileview_datelayout);
        dateLayout.setVisibility(View.VISIBLE);
        dateTextView = (TextView) layout.findViewById(R.id.profileview_date);

        setContent();
        return layout;
    }

    private void setContent() {
        Profile profile = null;
        ProfileSwitch profileSwitch = MainApp.getConfigBuilder().getProfileSwitchFromHistory(time);
        if(profileSwitch!=null && profileSwitch.profileJson != null){
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
        } else {
            noProfile.setVisibility(View.VISIBLE);
        }
    }
}
