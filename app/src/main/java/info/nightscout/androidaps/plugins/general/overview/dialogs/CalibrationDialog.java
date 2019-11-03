package info.nightscout.androidaps.plugins.general.overview.dialogs;


import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.XdripCalibrations;

public class CalibrationDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(CalibrationDialog.class);

    NumberPicker bgNumber;
    TextView unitsView;

    Context context;

    public CalibrationDialog() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.context = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_calibration_dialog, container, false);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        String units = ProfileFunctions.getInstance().getProfileUnits();
        Double bg = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, units);

        bgNumber = (NumberPicker) view.findViewById(R.id.overview_calibration_bg);

        if (units.equals(Constants.MMOL))
            bgNumber.setParams(bg, 0d, 30d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok));
        else
            bgNumber.setParams(bg, 0d, 500d, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        unitsView = (TextView) view.findViewById(R.id.overview_calibration_units);
        unitsView.setText(units);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                final Double bg = SafeParse.stringToDouble(bgNumber.getText());
                XdripCalibrations.confirmAndSendCalibration(bg, context);
                dismiss();
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }
}
