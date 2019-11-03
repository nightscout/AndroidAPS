package info.nightscout.androidaps.plugins.general.actions.dialogs;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SafeParse;

public class NewTempBasalDialog extends DialogFragment implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(NewTempBasalDialog.class);

    RadioButton percentRadio;
    RadioButton absoluteRadio;
    RadioGroup basalTypeRadioGroup;
    LinearLayout typeSelectorLayout;

    LinearLayout percentLayout;
    LinearLayout absoluteLayout;

    NumberPicker basalPercent;
    NumberPicker basalAbsolute;
    NumberPicker duration;

    public NewTempBasalDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(MainApp.gs(R.string.overview_tempbasal_button));

        View view = inflater.inflate(R.layout.overview_newtempbasal_dialog, container, false);

        percentLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_percent_layout);
        absoluteLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_absolute_layout);
        percentRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_percent_radio);
        basalTypeRadioGroup = (RadioGroup) view.findViewById(R.id.overview_newtempbasal_radiogroup);
        absoluteRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_absolute_radio);
        typeSelectorLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_typeselector_layout);

        PumpDescription pumpDescription = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription();

        basalPercent = (NumberPicker) view.findViewById(R.id.overview_newtempbasal_basalpercentinput);
        double maxTempPercent = pumpDescription.maxTempPercent;
        double tempPercentStep = pumpDescription.tempPercentStep;
        basalPercent.setParams(100d, 0d, maxTempPercent, tempPercentStep, new DecimalFormat("0"), true, view.findViewById(R.id.ok));

        Profile profile = ProfileFunctions.getInstance().getProfile();
        Double currentBasal = profile != null ? profile.getBasal() : 0d;
        basalAbsolute = (NumberPicker) view.findViewById(R.id.overview_newtempbasal_basalabsoluteinput);
        basalAbsolute.setParams(currentBasal, 0d, pumpDescription.maxTempAbsolute, pumpDescription.tempAbsoluteStep, new DecimalFormat("0.00"), true, view.findViewById(R.id.ok));

        double tempDurationStep = pumpDescription.tempDurationStep;
        double tempMaxDuration = pumpDescription.tempMaxDuration;
        duration = (NumberPicker) view.findViewById(R.id.overview_newtempbasal_duration);
        duration.setParams(tempDurationStep, tempDurationStep, tempMaxDuration, tempDurationStep, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        if ((pumpDescription.tempBasalStyle & PumpDescription.PERCENT) == PumpDescription.PERCENT && (pumpDescription.tempBasalStyle & PumpDescription.ABSOLUTE) == PumpDescription.ABSOLUTE) {
            // Both allowed
            typeSelectorLayout.setVisibility(View.VISIBLE);
        } else {
            typeSelectorLayout.setVisibility(View.GONE);
        }

        if ((pumpDescription.tempBasalStyle & PumpDescription.PERCENT) == PumpDescription.PERCENT) {
            percentRadio.setChecked(true);
            absoluteRadio.setChecked(false);
            percentLayout.setVisibility(View.VISIBLE);
            absoluteLayout.setVisibility(View.GONE);
        } else if ((pumpDescription.tempBasalStyle & PumpDescription.ABSOLUTE) == PumpDescription.ABSOLUTE) {
            percentRadio.setChecked(false);
            absoluteRadio.setChecked(true);
            percentLayout.setVisibility(View.GONE);
            absoluteLayout.setVisibility(View.VISIBLE);
        }

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);
        basalTypeRadioGroup.setOnCheckedChangeListener(this);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                try {
                    int percent = 0;
                    Double absolute = 0d;
                    final boolean setAsPercent = percentRadio.isChecked();
                    int durationInMinutes = SafeParse.stringToInt(duration.getText());

                    Profile profile = ProfileFunctions.getInstance().getProfile();
                    if (profile == null)
                        return;

                    String confirmMessage = MainApp.gs(R.string.setbasalquestion);
                    if (setAsPercent) {
                        int basalPercentInput = SafeParse.stringToInt(basalPercent.getText());
                        percent = MainApp.getConstraintChecker().applyBasalPercentConstraints(new Constraint<>(basalPercentInput), profile).value();
                        confirmMessage += "\n" + percent + "% ";
                        confirmMessage += "\n" + MainApp.gs(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (percent != basalPercentInput)
                            confirmMessage += "\n" + MainApp.gs(R.string.constraintapllied);
                    } else {
                        Double basalAbsoluteInput = SafeParse.stringToDouble(basalAbsolute.getText());
                        absolute = MainApp.getConstraintChecker().applyBasalConstraints(new Constraint<>(basalAbsoluteInput), profile).value();
                        confirmMessage += "\n" + absolute + " U/h ";
                        confirmMessage += "\n" + MainApp.gs(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (Math.abs(absolute - basalAbsoluteInput) > 0.01d)
                            confirmMessage += "\n" + MainApp.gs(R.string.constraintapllied);
                    }

                    final int finalBasalPercent = percent;
                    final Double finalBasal = absolute;
                    final int finalDurationInMinutes = durationInMinutes;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(MainApp.gs(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Callback callback = new Callback() {
                                @Override
                                public void run() {
                                    if (!result.success) {
                                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                        i.putExtra("soundid", R.raw.boluserror);
                                        i.putExtra("status", result.comment);
                                        i.putExtra("title", MainApp.gs(R.string.tempbasaldeliveryerror));
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        MainApp.instance().startActivity(i);
                                    }
                                }
                            };
                            if (setAsPercent) {
                                ConfigBuilderPlugin.getPlugin().getCommandQueue().tempBasalPercent(finalBasalPercent, finalDurationInMinutes, true, profile, callback);
                            } else {
                                ConfigBuilderPlugin.getPlugin().getCommandQueue().tempBasalAbsolute(finalBasal, finalDurationInMinutes, true, profile, callback);
                            }
                        }
                    });
                    builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                    builder.show();
                    dismiss();

                } catch (Exception e) {
                    log.error("Unhandled exception", e);
                }
                break;
            case R.id.cancel:
                dismiss();
                break;
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
