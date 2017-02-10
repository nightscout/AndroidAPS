package info.nightscout.androidaps.plugins.Overview.Dialogs;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class CalibrationDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(CalibrationDialog.class);

    Button okButton;
    PlusMinusEditText bgText;
    TextView unitsView;

    Context parentContext;

    public CalibrationDialog() {
        // Required empty public constructor
    }

    public void setContext(Context context) {
        parentContext = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_calibration_dialog, container, false);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        okButton = (Button) view.findViewById(R.id.overview_calibration_okbutton);
        okButton.setOnClickListener(this);

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        Double bg = NSProfile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, profile.getUnits());
        if (profile.getUnits().equals(Constants.MMOL))
            bgText = new PlusMinusEditText(view, R.id.overview_calibration_bg, R.id.overview_calibration_bg_plus, R.id.overview_calibration_bg_minus, bg, 0d, 30d, 0.1d, new DecimalFormat("0.0"), false);
        else
            bgText = new PlusMinusEditText(view, R.id.overview_calibration_bg, R.id.overview_calibration_bg_plus, R.id.overview_calibration_bg_minus, bg, 0d, 500d, 1d, new DecimalFormat("0"), false);

        unitsView = (TextView) view.findViewById(R.id.overview_calibration_units);
        unitsView.setText(profile.getUnits());

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_calibration_okbutton:
                if (parentContext != null) {
                    final NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();

                    final Double bg = bgText.getValue();

                    String confirmMessage = String.format(MainApp.sResources.getString(R.string.send_calibration), bg);

                    AlertDialog.Builder builder = new AlertDialog.Builder(parentContext);
                    builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Context context = MainApp.instance().getApplicationContext();
                            Bundle bundle = new Bundle();
                            bundle.putDouble("glucose_number", bg);
                            bundle.putString("units", profile.getUnits().equals(Constants.MGDL) ? "mgdl" : "mmol");
                            bundle.putLong("timestamp", new Date().getTime());
                            Intent intent = new Intent(Intents.ACTION_REMOTE_CALIBRATION);
                            intent.putExtras(bundle);
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            context.sendBroadcast(intent);
                            List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(intent, 0);
                            if (q.size() < 1) {
                                ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.xdripnotinstalled));
                                log.debug(MainApp.sResources.getString(R.string.xdripnotinstalled));
                            } else {
                                ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.calibrationsent));
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();
                } else {
                    log.error("parentContext == null");
                }
                break;
        }
    }
}
