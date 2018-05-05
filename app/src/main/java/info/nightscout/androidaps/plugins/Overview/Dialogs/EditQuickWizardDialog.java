package info.nightscout.androidaps.plugins.Overview.Dialogs;


import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.QuickWizard;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventQuickWizardChange;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SafeParse;

public class EditQuickWizardDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(EditQuickWizardDialog.class);

    QuickWizardEntry entry = new QuickWizard().newEmptyItem();
    QuickWizard quickWizard = MainApp.getSpecificPlugin(OverviewPlugin.class).quickWizard;

    EditText buttonEdit;
    EditText carbsEdit;
    Spinner fromSpinner;
    Spinner toSpinner;
    Spinner useBGSpinner;
    Spinner useCOBSpinner;
    Spinner useBolusIOBSpinner;
    Spinner useBasalIOBSpinner;
    Spinner useTrendSpinner;
    Spinner useSuperBolusSpinner;
    Spinner useTempTargetSpinner;

    public EditQuickWizardDialog() {
    }

    public void setData(QuickWizardEntry data) {
        entry = data;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        View view = inflater.inflate(R.layout.overview_editquickwizard_dialog, container, false);
        buttonEdit = (EditText) view.findViewById(R.id.overview_editquickwizard_button_edit);
        carbsEdit = (EditText) view.findViewById(R.id.overview_editquickwizard_carbs_edit);
        fromSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_from_spinner);
        toSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_to_spinner);
        useBGSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usebg_spinner);
        useCOBSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usecob_spinner);
        useBolusIOBSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usebolusiob_spinner);
        useBasalIOBSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usebasaliob_spinner);
        useTrendSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usetrend_spinner);
        useSuperBolusSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usesuperbolus_spinner);
        useTempTargetSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_usetemptarget_spinner);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        int posFrom = 0;
        int posTo = 95;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        int pos = 0;
        for (int t = 0; t < 24 * 60 * 60; t += 15 * 60) {
            timeList.add(DateUtil.timeString(DateUtil.toDate(t)));
            if (entry.validFrom() == t) posFrom = pos;
            if (entry.validTo() == t) posTo = pos;
            pos++;
        }
        timeList.add(DateUtil.timeString(DateUtil.toDate(24 * 60 * 60 - 60)));

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                R.layout.spinner_centered, timeList);
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);

        buttonEdit.setText(entry.buttonText());
        carbsEdit.setText(entry.carbs().toString());
        fromSpinner.setSelection(posFrom);
        toSpinner.setSelection(posTo);

        setSelection(useBGSpinner, entry.useBG());
        setSelection(useCOBSpinner, entry.useCOB());
        setSelection(useBolusIOBSpinner, entry.useBolusIOB());
        setSelection(useBasalIOBSpinner, entry.useBasalIOB());
        setSelection(useTrendSpinner, entry.useTrend());
        setSelection(useSuperBolusSpinner, entry.useSuperBolus());
        setSelection(useTempTargetSpinner, entry.useTempTarget());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ok:
                if (fromSpinner.getSelectedItem() == null) return;
                if (toSpinner.getSelectedItem() == null) return;
                try {
                    entry.storage.put("buttonText", buttonEdit.getText().toString());
                    entry.storage.put("carbs", SafeParse.stringToInt(carbsEdit.getText().toString()));
                    int validFromInt = DateUtil.toSeconds(fromSpinner.getSelectedItem().toString());
                    entry.storage.put("validFrom", validFromInt);
                    int validToInt = DateUtil.toSeconds(toSpinner.getSelectedItem().toString());
                    entry.storage.put("validTo", validToInt);
                    entry.storage.put("useBG", getSelection(useBGSpinner));
                    entry.storage.put("useCOB", getSelection(useCOBSpinner));
                    entry.storage.put("useBolusIOB", getSelection(useBolusIOBSpinner));
                    entry.storage.put("useBasalIOB", getSelection(useBasalIOBSpinner));
                    entry.storage.put("useTrend", getSelection(useTrendSpinner));
                    entry.storage.put("useSuperBolus", getSelection(useSuperBolusSpinner));
                    entry.storage.put("useTempTarget", getSelection(useTempTargetSpinner));
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
                quickWizard.addOrUpdate(entry);
                dismiss();
                MainApp.bus().post(new EventQuickWizardChange());
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    int getSelection(Spinner spinner) {
        String value = spinner.getSelectedItem().toString();
        if (value.equals(MainApp.gs(R.string.yes)))
            return QuickWizardEntry.YES;
        if (value.equals(MainApp.gs(R.string.no)))
            return QuickWizardEntry.NO;
        if (value.equals(MainApp.gs(R.string.positiveonly)))
            return QuickWizardEntry.POSITIVE_ONLY;
        if (value.equals(MainApp.gs(R.string.negativeonly)))
            return QuickWizardEntry.NEGATIVE_ONLY;
        return QuickWizardEntry.NO;
    }

    void setSelection(Spinner spinner, int value) {
        String selection;
        switch (value) {
            case QuickWizardEntry.YES:
                selection = MainApp.gs(R.string.yes);
                break;
            case QuickWizardEntry.NO:
                selection = MainApp.gs(R.string.no);
                break;
            case QuickWizardEntry.POSITIVE_ONLY:
                selection = MainApp.gs(R.string.positiveonly);
                break;
            case QuickWizardEntry.NEGATIVE_ONLY:
                selection = MainApp.gs(R.string.negativeonly);
                break;
            default:
                selection = MainApp.gs(R.string.no);
                break;
        }

        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(selection)) {
                spinner.setSelection(i);
                break;
            }
        }
    }
}
