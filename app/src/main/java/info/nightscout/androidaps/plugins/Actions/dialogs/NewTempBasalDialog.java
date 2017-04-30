package info.nightscout.androidaps.plugins.Actions.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;

public class NewTempBasalDialog extends DialogFragment implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {

    Button okButton;
    EditText basalPercentEdit;
    EditText basalAbsoluteEdit;
    RadioButton percentRadio;
    RadioButton absoluteRadio;
    RadioGroup basalTypeRadioGroup;
    RadioButton h05Radio;
    RadioButton h10Radio;
    RadioButton h20Radio;
    RadioButton h30Radio;
    RadioButton h40Radio;

    LinearLayout percentLayout;
    LinearLayout absoluteLayout;

    PlusMinusEditText basalPercentPM;
    PlusMinusEditText basalAbsolutePM;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public NewTempBasalDialog() {
        mHandlerThread = new HandlerThread(NewTempBasalDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.overview_tempbasal_button));

        View view = inflater.inflate(R.layout.overview_newtempbasal_dialog, container, false);
        okButton = (Button) view.findViewById(R.id.overview_newtempbasal_okbutton);
        basalPercentEdit = (EditText) view.findViewById(R.id.overview_newtempbasal_basalpercentinput);
        basalAbsoluteEdit = (EditText) view.findViewById(R.id.overview_newtempbasal_basalabsoluteinput);
        percentLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_percent_layout);
        absoluteLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_absolute_layout);
        percentRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_percent_radio);
        basalTypeRadioGroup = (RadioGroup) view.findViewById(R.id.overview_newtempbasal_radiogroup);
        absoluteRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_absolute_radio);
        h05Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_05h);
        h10Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_1h);
        h20Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_2h);
        h30Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_3h);
        h40Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_4h);

        Integer maxPercent = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalPercentOnlyForCheckLimit);
        basalPercentPM = new PlusMinusEditText(view, R.id.overview_newtempbasal_basalpercentinput, R.id.overview_newtempbasal_basalpercent_plus, R.id.overview_newtempbasal_basalpercent_minus, 100d, 0d, (double) maxPercent, 5d, new DecimalFormat("0"), true);

        Double maxAbsolute = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalAbsoluteOnlyForCheckLimit);
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        Double currentBasal = 0d;
        if (profile != null) currentBasal = profile.getBasal(NSProfile.secondsFromMidnight());
        basalAbsolutePM = new PlusMinusEditText(view, R.id.overview_newtempbasal_basalabsoluteinput, R.id.overview_newtempbasal_basalabsolute_plus, R.id.overview_newtempbasal_basalabsolute_minus, currentBasal, 0d, maxAbsolute, 0.05d, new DecimalFormat("0.00"), true);

        absoluteLayout.setVisibility(View.GONE);
        okButton.setOnClickListener(this);
        basalTypeRadioGroup.setOnCheckedChangeListener(this);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_newtempbasal_okbutton:
                try {
                    int basalPercent = 0;
                    Double basalAbsolute = 0d;
                    final boolean setAsPercent = percentRadio.isChecked();
                    int durationInMinutes = 30;
                    if (h10Radio.isChecked()) durationInMinutes = 60;
                    if (h20Radio.isChecked()) durationInMinutes = 120;
                    if (h30Radio.isChecked()) durationInMinutes = 180;
                    if (h40Radio.isChecked()) durationInMinutes = 240;

                    String confirmMessage = getString(R.string.setbasalquestion);
                    if (setAsPercent) {
                        int basalPercentInput = SafeParse.stringToDouble(basalPercentEdit.getText().toString()).intValue();
                        basalPercent = MainApp.getConfigBuilder().applyBasalConstraints(basalPercentInput);
                        confirmMessage += "\n" + basalPercent + "% ";
                        confirmMessage += "\n" + getString(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (basalPercent != basalPercentInput)
                            confirmMessage += "\n" + getString(R.string.constraintapllied);
                    } else {
                        Double basalAbsoluteInput = SafeParse.stringToDouble(basalAbsoluteEdit.getText().toString());
                        basalAbsolute = MainApp.getConfigBuilder().applyBasalConstraints(basalAbsoluteInput);
                        confirmMessage += "\n" + basalAbsolute + " U/h ";
                        confirmMessage += "\n" + getString(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (basalAbsolute - basalAbsoluteInput != 0d)
                            confirmMessage += "\n" + getString(R.string.constraintapllied);
                    }

                    final int finalBasalPercent = basalPercent;
                    final Double finalBasal = basalAbsolute;
                    final int finalDurationInMinutes = durationInMinutes;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            final PumpInterface pump = MainApp.getConfigBuilder();
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    PumpEnactResult result;
                                    if (setAsPercent) {
                                        result = pump.setTempBasalPercent(finalBasalPercent, finalDurationInMinutes);
                                    } else {
                                        result = pump.setTempBasalAbsolute(finalBasal, finalDurationInMinutes);
                                    }
                                    if (!result.success) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setTitle(MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                        builder.show();
                                    }
                                }
                            });
                            Answers.getInstance().logCustom(new CustomEvent("TempBasal"));
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();

                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.overview_newtempbasal_percent_radio:
                percentLayout.setVisibility(View.VISIBLE);
                absoluteLayout.setVisibility(View.GONE);
                break;
            case R.id.overview_newtempbasal_absolute_radio:
                percentLayout.setVisibility(View.GONE);
                absoluteLayout.setVisibility(View.VISIBLE);
                break;
        }
    }
}
