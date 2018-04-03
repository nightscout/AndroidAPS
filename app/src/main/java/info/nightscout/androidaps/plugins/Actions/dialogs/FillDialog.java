package info.nightscout.androidaps.plugins.Actions.dialogs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;

import com.crashlytics.android.answers.CustomEvent;
import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

public class FillDialog extends DialogFragment implements OnClickListener {
    private static Logger log = LoggerFactory.getLogger(FillDialog.class);

    double amount1 = 0d;
    double amount2 = 0d;
    double amount3 = 0d;

    NumberPicker editInsulin;
    CheckBox pumpSiteChangeCheckbox;
    CheckBox insulinCartridgeChangeCheckbox;

    public FillDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.actions_fill_dialog, null, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        pumpSiteChangeCheckbox = view.findViewById(R.id.catheter_change);
        insulinCartridgeChangeCheckbox = view.findViewById(R.id.cartridge_change);

        Double maxInsulin = MainApp.getConstraintChecker().getMaxBolusAllowed().value();
        double bolusstep = ConfigBuilderPlugin.getActivePump().getPumpDescription().bolusStep;
        editInsulin = view.findViewById(R.id.treatments_newtreatment_insulinamount);
        editInsulin.setParams(0d, 0d, maxInsulin, bolusstep, DecimalFormatter.pumpSupportedBolusFormat(), false);

        //setup preset buttons
        Button button1 = (Button) view.findViewById(R.id.fill_preset_button1);
        Button button2 = (Button) view.findViewById(R.id.fill_preset_button2);
        Button button3 = (Button) view.findViewById(R.id.fill_preset_button3);

        amount1 = SP.getDouble("fill_button1", 0.3);
        amount2 = SP.getDouble("fill_button2", 0d);
        amount3 = SP.getDouble("fill_button3", 0d);

        if (amount1 > 0) {
            button1.setVisibility(View.VISIBLE);
            button1.setText(DecimalFormatter.toPumpSupportedBolus(amount1)); // + "U");
            button1.setOnClickListener(this);
        } else {
            button1.setVisibility(View.GONE);
        }
        if (amount2 > 0) {
            button2.setVisibility(View.VISIBLE);
            button2.setText(DecimalFormatter.toPumpSupportedBolus(amount2)); // + "U");
            button2.setOnClickListener(this);
        } else {
            button2.setVisibility(View.GONE);
        }
        if (amount3 > 0) {
            button3.setVisibility(View.VISIBLE);
            button3.setText(DecimalFormatter.toPumpSupportedBolus(amount3)); // + "U");
            button3.setOnClickListener(this);
        } else {
            button3.setVisibility(View.GONE);
        }

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

    private void confirmAndDeliver() {
        try {
            Double insulin = SafeParse.stringToDouble(editInsulin.getText());

            List<String> confirmMessage = new LinkedList<>();

            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(insulin)).value();
            if (insulinAfterConstraints > 0) {
                confirmMessage.add(MainApp.gs(R.string.fillwarning));
                confirmMessage.add("");
                confirmMessage.add(MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.colorCarbsButton) + "'>" + insulinAfterConstraints + "U" + "</font>");
                if (!insulinAfterConstraints.equals(insulin))
                    confirmMessage.add("<font color='" + MainApp.sResources.getColor(R.color.low) + "'>" + MainApp.gs(R.string.bolusconstraintapplied) + "</font>");
            }

            if (pumpSiteChangeCheckbox.isChecked())
                confirmMessage.add("" + "<font color='" + MainApp.sResources.getColor(R.color.high) + "'>" + getString(R.string.record_pump_site_change) +  "</font>");

            if (insulinCartridgeChangeCheckbox.isChecked())
                confirmMessage.add("" + "<font color='" + MainApp.sResources.getColor(R.color.high) + "'>" + getString(R.string.record_insulin_cartridge_change) + "</font>");

            final Double finalInsulinAfterConstraints = insulinAfterConstraints;

            final Context context = getContext();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            if (confirmMessage.isEmpty())
                confirmMessage.add(MainApp.gs(R.string.no_action_selected));

            builder.setTitle(MainApp.gs(R.string.confirmation));
            builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(confirmMessage)));
            builder.setPositiveButton(getString(R.string.primefill), (dialog, id) -> {
                if (finalInsulinAfterConstraints > 0) {
                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                    detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                    detailedBolusInfo.context = context;
                    detailedBolusInfo.source = Source.USER;
                    detailedBolusInfo.isValid = false; // do not count it in IOB (for pump history)
                    ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                        @Override
                        public void run() {
                            if (!result.success) {
                                Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                i.putExtra("soundid", R.raw.boluserror);
                                i.putExtra("status", result.comment);
                                i.putExtra("title", MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                MainApp.instance().startActivity(i);
                            }
                        }
                    });
                    FabricPrivacy.getInstance().logCustom(new CustomEvent("Fill"));
                }
                long now = System.currentTimeMillis();
                if (pumpSiteChangeCheckbox.isChecked()) NSUpload.uploadEvent(CareportalEvent.SITECHANGE, now);
                if (insulinCartridgeChangeCheckbox.isChecked()) NSUpload.uploadEvent(CareportalEvent.INSULINCHANGE, now + 1000);
            });
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
            dismiss();
        } catch (RuntimeException e) {
            log.error("Unhandled exception", e);
        }
    }

}