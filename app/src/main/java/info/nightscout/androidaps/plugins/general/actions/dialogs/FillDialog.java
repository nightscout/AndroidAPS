package info.nightscout.androidaps.plugins.general.actions.dialogs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.ToastUtils;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class FillDialog extends DialogFragment implements OnClickListener {
    private static Logger log = LoggerFactory.getLogger(FillDialog.class);

    private CheckBox pumpSiteChangeCheckbox;
    private CheckBox insulinCartridgeChangeCheckbox;

    private NumberPicker editInsulin;

    double amount1 = 0d;
    double amount2 = 0d;
    double amount3 = 0d;

    private EditText notesEdit;

    //one shot guards
    private boolean accepted;
    private boolean okClicked;

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            validateInputs();
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private void validateInputs() {
        int time = editInsulin.getValue().intValue();
        if (Math.abs(time) > 12 * 60) {
            editInsulin.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.constraintapllied));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.actions_fill_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        pumpSiteChangeCheckbox = view.findViewById(R.id.fill_catheter_change);
        insulinCartridgeChangeCheckbox = view.findViewById(R.id.fill_cartridge_change);

        Double maxInsulin = MainApp.getConstraintChecker().getMaxBolusAllowed().value();
        double bolusstep = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().bolusStep;
        editInsulin = view.findViewById(R.id.fill_insulinamount);
        editInsulin.setParams(0d, 0d, maxInsulin, bolusstep, DecimalFormatter.pumpSupportedBolusFormat(), false, textWatcher);


        Button preset1Button = view.findViewById(R.id.fill_preset_button1);
        amount1 = SP.getDouble("fill_button1", 0.3);
        if (amount1 > 0) {
            preset1Button.setVisibility(View.VISIBLE);
            preset1Button.setText(DecimalFormatter.toPumpSupportedBolus(amount1)); // + "U");
            preset1Button.setOnClickListener(this);
        } else {
            preset1Button.setVisibility(View.GONE);
        }
        Button preset2Button = view.findViewById(R.id.fill_preset_button2);
        amount2 = SP.getDouble("fill_button2", 0d);
        if (amount2 > 0) {
            preset2Button.setVisibility(View.VISIBLE);
            preset2Button.setText(DecimalFormatter.toPumpSupportedBolus(amount2)); // + "U");
            preset2Button.setOnClickListener(this);
        } else {
            preset2Button.setVisibility(View.GONE);
        }
        Button preset3Button = view.findViewById(R.id.fill_preset_button3);
        amount3 = SP.getDouble("fill_button3", 0d);
        if (amount3 > 0) {
            preset3Button.setVisibility(View.VISIBLE);
            preset3Button.setText(DecimalFormatter.toPumpSupportedBolus(amount3)); // + "U");
            preset3Button.setOnClickListener(this);
        } else {
            preset3Button.setVisibility(View.GONE);
        }

        LinearLayout notesLayout = view.findViewById(R.id.fill_notes_layout);
        notesLayout.setVisibility(SP.getBoolean(R.string.key_show_notes_entry_dialogs, false) ? View.VISIBLE : View.GONE);
        notesEdit = view.findViewById(R.id.fill_notes);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                confirmAndDeliver();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.fill_preset_button1:
                editInsulin.setValue(amount1);
                break;
            case R.id.fill_preset_button2:
                editInsulin.setValue(amount2);
                break;
            case R.id.fill_preset_button3:
                editInsulin.setValue(amount3);
                break;
        }

    }

    private synchronized void confirmAndDeliver() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;

        try {
            Double insulin = SafeParse.stringToDouble(editInsulin.getText());

            List<String> confirmMessage = new LinkedList<>();

            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(insulin)).value();
            if (insulinAfterConstraints > 0) {
                confirmMessage.add(MainApp.gs(R.string.fillwarning));
                confirmMessage.add("");
                confirmMessage.add(MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.colorCarbsButton) + "'>" + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints) + "U" + "</font>");
                if (Math.abs(insulinAfterConstraints - insulin) > 0.01d)
                    confirmMessage.add("<font color='" + MainApp.gc(R.color.low) + "'>" + MainApp.gs(R.string.bolusconstraintapplied) + "</font>");
            }

            if (pumpSiteChangeCheckbox.isChecked())
                confirmMessage.add("" + "<font color='" + MainApp.gc(R.color.actionsConfirm) + "'>" + MainApp.gs(R.string.record_pump_site_change) + "</font>");

            if (insulinCartridgeChangeCheckbox.isChecked())
                confirmMessage.add("" + "<font color='" + MainApp.gc(R.color.actionsConfirm) + "'>" + MainApp.gs(R.string.record_insulin_cartridge_change) + "</font>");

            final String notes = notesEdit.getText().toString();
            if (!notes.isEmpty()) {
                confirmMessage.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes);
            }

            final Double finalInsulinAfterConstraints = insulinAfterConstraints;

            final Context context = getContext();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setTitle(MainApp.gs(R.string.confirmation));
            if (insulinAfterConstraints > 0 || pumpSiteChangeCheckbox.isChecked() || insulinCartridgeChangeCheckbox.isChecked()) {
                builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(confirmMessage)));
                builder.setPositiveButton(MainApp.gs(R.string.primefill), (dialog, id) -> {
                    synchronized (builder) {
                        if (accepted) {
                            log.debug("guarding: already accepted");
                            return;
                        }
                        accepted = true;

                        if (finalInsulinAfterConstraints > 0) {
                            DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                            detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                            detailedBolusInfo.context = context;
                            detailedBolusInfo.source = Source.USER;
                            detailedBolusInfo.isValid = false; // do not count it in IOB (for pump history)
                            detailedBolusInfo.notes = notes;
                            ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                                @Override
                                public void run() {
                                    if (!result.success) {
                                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                        i.putExtra("soundid", R.raw.boluserror);
                                        i.putExtra("status", result.comment);
                                        i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror));
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        MainApp.instance().startActivity(i);
                                    }
                                }
                            });
                        }
                        if (pumpSiteChangeCheckbox.isChecked())
                            NSUpload.uploadEvent(CareportalEvent.SITECHANGE, now(), notes);
                        if (insulinCartridgeChangeCheckbox.isChecked())
                            NSUpload.uploadEvent(CareportalEvent.INSULINCHANGE, now() + 1000, notes);
                    }
                });
            } else {
                builder.setMessage(MainApp.gs(R.string.no_action_selected));
            }
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
            builder.show();
            dismiss();
        } catch (RuntimeException e) {
            log.error("Unhandled exception", e);
        }
    }

}