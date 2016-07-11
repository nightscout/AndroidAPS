package info.nightscout.androidaps.plugins.DanaR.Dialogs;

import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 10.07.2016.
 */
public class ProfileViewDialog extends DialogFragment {
    private static Logger log = LoggerFactory.getLogger(ProfileViewDialog.class);

    private static TextView noProfile;
    private static TextView units;
    private static TextView dia;
    private static TextView activeProfile;
    private static TextView ic;
    private static TextView isf;
    private static TextView basal;
    private static TextView target;

    private static Button refreshButton;

    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

    Handler mHandler;
    static HandlerThread mHandlerThread;

    NSProfile profile = null;

    public ProfileViewDialog() {
        mHandlerThread = new HandlerThread(ProfileViewDialog.class.getSimpleName());
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper());
        profile = ((DanaRFragment) MainActivity.getSpecificPlugin(DanaRFragment.class)).getProfile();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.nsprofileviewer_fragment, container, false);

        noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
        units = (TextView) layout.findViewById(R.id.profileview_units);
        dia = (TextView) layout.findViewById(R.id.profileview_dia);
        activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
        ic = (TextView) layout.findViewById(R.id.profileview_ic);
        isf = (TextView) layout.findViewById(R.id.profileview_isf);
        basal = (TextView) layout.findViewById(R.id.profileview_basal);
        target = (TextView) layout.findViewById(R.id.profileview_target);
        refreshButton = (Button) layout.findViewById(R.id.profileview_reload);
        refreshButton.setVisibility(View.VISIBLE);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        DanaRFragment.getDanaRPump().lastSettingsRead = new Date(0);
                        DanaRFragment.getDanaConnection().connectIfNotConnected("ProfileViewDialog");
                        //refreshButton.setVisibility(View.VISIBLE);
                    }
                });
                //refreshButton.setVisibility(View.GONE);
                dismiss();
            }
        });

        setContent();
        return layout;
    }

    private void setContent() {
        if (profile == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }
        units.setText(profile.getUnits());
        dia.setText(formatNumber2decimalplaces.format(profile.getDia()) + " h");
        activeProfile.setText(profile.getActiveProfile());
        ic.setText(profile.getIcList());
        isf.setText(profile.getIsfList());
        basal.setText(profile.getBasalList());
        target.setText(profile.getTargetList());
    }


}
