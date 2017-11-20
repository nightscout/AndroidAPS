package info.nightscout.androidaps.plugins.ProfileCircadianPercentage;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.andreabaccega.widget.FormEditText;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SafeParse;

public class CircadianPercentageProfileFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(CircadianPercentageProfileFragment.class);

    private static CircadianPercentageProfilePlugin circadianPercentageProfilePlugin = new CircadianPercentageProfilePlugin();
    private Object snackbarCaller;

    public static CircadianPercentageProfilePlugin getPlugin() {
        return circadianPercentageProfilePlugin;
    }

    FormEditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    FormEditText targetlowView;
    FormEditText targethighView;
    FormEditText percentageView;
    FormEditText timeshiftView;
    TextView profileView;
    TextView baseprofileIC;
    TextView baseprofileBasal;
    LinearLayout baseprofileBasalLayout;
    TextView baseprofileISF;
    Button profileswitchButton;
    ImageView percentageIcon;
    ImageView timeIcon;
    ImageView basaleditIcon;
    ImageView iceditIcon;
    ImageView isfeditIcon;
    BasalEditDialog basalEditDialog;
    FrameLayout fl;
    Snackbar mSnackBar;

    static Boolean percentageViewHint = true;
    static Boolean timeshiftViewHint = true;

    TextWatcher textWatch = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start,
                                      int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start,
                                  int before, int count) {

            if (percentageView.testValidity()) {
                if (SafeParse.stringToInt(percentageView.getText().toString()) == 0) {
                    circadianPercentageProfilePlugin.percentage = 100;
                } else {
                    circadianPercentageProfilePlugin.percentage = SafeParse.stringToInt(percentageView.getText().toString());
                }
                updateProfileInfo();
            }
            if (timeshiftView.testValidity()) {
                circadianPercentageProfilePlugin.timeshift = SafeParse.stringToInt(timeshiftView.getText().toString());
                updateProfileInfo();
            }
            if (diaView.testValidity()) {
                circadianPercentageProfilePlugin.dia = SafeParse.stringToDouble(diaView.getText().toString());
                updateProfileInfo();
            }
            if (targethighView.testValidity()) {
                circadianPercentageProfilePlugin.targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                updateProfileInfo();
            }
            if (targetlowView.testValidity()) {
                circadianPercentageProfilePlugin.targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                updateProfileInfo();
            }
            circadianPercentageProfilePlugin.storeSettings();
            updateProfileInfo();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        showDeprecatedDialog();

        View layout = inflater.inflate(R.layout.circadianpercentageprofile_fragment, container, false);
        fl = (FrameLayout) layout.findViewById(R.id.circadianpercentageprofile_framelayout);
        fl.requestFocusFromTouch();
        diaView = (FormEditText) layout.findViewById(R.id.circadianpercentageprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.circadianpercentageprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.circadianpercentageprofile_mmol);
        targetlowView = (FormEditText) layout.findViewById(R.id.circadianpercentageprofile_targetlow);
        targethighView = (FormEditText) layout.findViewById(R.id.circadianpercentageprofile_targethigh);
        percentageView = (FormEditText) layout.findViewById(R.id.circadianpercentageprofile_percentage);
        timeshiftView = (FormEditText) layout.findViewById(R.id.circadianpercentageprofile_timeshift);
        profileView = (TextView) layout.findViewById(R.id.circadianpercentageprofile_profileview);
        baseprofileBasal = (TextView) layout.findViewById(R.id.circadianpercentageprofile_baseprofilebasal);
        baseprofileBasalLayout = (LinearLayout) layout.findViewById(R.id.circadianpercentageprofile_baseprofilebasal_layout);
        baseprofileIC = (TextView) layout.findViewById(R.id.circadianpercentageprofile_baseprofileic);
        baseprofileISF = (TextView) layout.findViewById(R.id.circadianpercentageprofile_baseprofileisf);
        percentageIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_percentageicon);
        timeIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_timeicon);
        profileswitchButton = (Button) layout.findViewById(R.id.circadianpercentageprofile_profileswitch);

        basaleditIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_basaledit);
        iceditIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_icedit);
        isfeditIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_isfedit);

        if (!ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable) {
            layout.findViewById(R.id.circadianpercentageprofile_baseprofilebasal_layout).setVisibility(View.GONE);
        }

        mgdlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                circadianPercentageProfilePlugin.mgdl = mgdlView.isChecked();
                circadianPercentageProfilePlugin.mmol = !circadianPercentageProfilePlugin.mgdl;
                mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
                circadianPercentageProfilePlugin.storeSettings();
                updateProfileInfo();
            }
        });
        mmolView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                circadianPercentageProfilePlugin.mmol = mmolView.isChecked();
                circadianPercentageProfilePlugin.mgdl = !circadianPercentageProfilePlugin.mmol;
                mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
                circadianPercentageProfilePlugin.storeSettings();
                updateProfileInfo();
            }
        });

        profileswitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCHDIRECT;
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
            }
        });

        timeIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                timeshiftView.requestFocusFromTouch();
                timeshiftView.setSelection(timeshiftView.getText().length());
                ((InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(timeshiftView, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        percentageIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                percentageView.requestFocusFromTouch();
                percentageView.setSelection(percentageView.getText().length());
                ((InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(percentageView, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        basaleditIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                basalEditDialog = new BasalEditDialog();
                basalEditDialog.setup(getPlugin().basebasal, getString(R.string.edit_base_basal), CircadianPercentageProfileFragment.this);
                basalEditDialog.show(getFragmentManager(), getString(R.string.edit_base_basal));
            }
        });

        isfeditIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                basalEditDialog = new BasalEditDialog();
                basalEditDialog.setup(getPlugin().baseisf, getString(R.string.edit_base_isf), CircadianPercentageProfileFragment.this);
                basalEditDialog.show(getFragmentManager(), getString(R.string.edit_base_isf));
            }
        });

        iceditIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                basalEditDialog = new BasalEditDialog();
                basalEditDialog.setup(getPlugin().baseic, getString(R.string.edit_base_ic), CircadianPercentageProfileFragment.this);
                basalEditDialog.show(getFragmentManager(), getString(R.string.edit_base_ic));
            }
        });

        /*timeshiftView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    if(mSnackBar!=null && snackbarCaller == timeshiftView){
                        mSnackBar.dismiss();
                    }
                    timeshiftView.clearFocus();
                    fl.requestFocusFromTouch();
                }
                else {
                    if (timeshiftViewHint) {
                        customSnackbar(view, getString(R.string.timeshift_hint), timeshiftView);
                    }
                }
            }
        });

        percentageView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    if(mSnackBar!=null && snackbarCaller == percentageView){
                        mSnackBar.dismiss();
                    }
                    percentageView.clearFocus();
                    fl.requestFocusFromTouch();
                }
                else {
                    if (percentageViewHint) {
                        customSnackbar(view, getString(R.string.percentagefactor_hint), percentageView);
                    }
                }
            }
        });*/

        diaView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    diaView.clearFocus();
                    fl.requestFocusFromTouch();
                }
            }
        });

        targethighView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    targethighView.clearFocus();
                    fl.requestFocusFromTouch();
                }
            }
        });

        targetlowView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    targetlowView.clearFocus();
                    fl.requestFocusFromTouch();
                }
            }
        });


        diaView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);
        percentageView.addTextChangedListener(textWatch);
        timeshiftView.addTextChangedListener(textWatch);

        updateGUI();

        onStatusEvent(new EventInitializationChanged());

        return layout;
    }

    private void showDeprecatedDialog() {
        AlertDialog.Builder adb = new AlertDialog.Builder(getContext());
        adb.setTitle("DEPRECATED! Please migrate!");
        adb.setMessage("CircadianPercentageProfile has been deprecated. " +
                "It is recommended to migrate to LocalProfile.\n\n" +
                "Good news: You won't lose any functionality! Percentage and Timeshift have been ported to the ProfileSwitch :) \n\n " +
                "How to migrate:\n" +
                "1) Press MIGRATE, the system will automatically fill the LocalProfile for you.\n" +
                "2) Switch to LocalProfile in the ConfigBuilder\n" +
                "3) CHECK that all settings are correct in the LocalProfile!!!");
        adb.setIcon(android.R.drawable.ic_dialog_alert);
        adb.setPositiveButton("MIGRATE", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                CircadianPercentageProfilePlugin.migrateToLP();
            }
        });
        adb.setNegativeButton("Cancel", null);
        adb.show();
    }

    public void updateGUI() {
        updateProfileInfo();

        diaView.removeTextChangedListener(textWatch);
        targetlowView.removeTextChangedListener(textWatch);
        targethighView.removeTextChangedListener(textWatch);
        percentageView.removeTextChangedListener(textWatch);
        timeshiftView.removeTextChangedListener(textWatch);

        mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
        mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
        diaView.setText(circadianPercentageProfilePlugin.dia.toString());
        targetlowView.setText(circadianPercentageProfilePlugin.targetLow.toString());
        targethighView.setText(circadianPercentageProfilePlugin.targetHigh.toString());
        percentageView.setText("" + circadianPercentageProfilePlugin.percentage);
        timeshiftView.setText("" + circadianPercentageProfilePlugin.timeshift);


        diaView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);
        percentageView.addTextChangedListener(textWatch);
        timeshiftView.addTextChangedListener(textWatch);

    }

    private void customSnackbar(View view, final String Msg, Object snackbarCaller) {
        if (mSnackBar != null) mSnackBar.dismiss();

        this.snackbarCaller = snackbarCaller;
        if (timeshiftViewHint || percentageViewHint) {
            //noinspection WrongConstant
            mSnackBar = Snackbar.make(view, Msg, 7000)
                    .setActionTextColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationInfo))
                    .setAction(getString(R.string.dont_show_again), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (Msg.equals(getString(R.string.percentagefactor_hint))) {
                                percentageViewHint = false;
                            } else if (Msg.equals(getString(R.string.timeshift_hint))) {
                                timeshiftViewHint = false;
                            }
                        }
                    });
            view = mSnackBar.getView();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            view.setLayoutParams(params);
            view.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.cardview_dark_background));
            TextView mainTextView = (TextView) (view).findViewById(android.support.design.R.id.snackbar_text);
            mainTextView.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.mdtp_white));
            mSnackBar.show();
        }
    }

    private void updateProfileInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>");
        sb.append(getString(R.string.nsprofileview_activeprofile_label));
        sb.append("</h3>");
        sb.append("<h4>");
        sb.append(getString(R.string.nsprofileview_basal_label));
        sb.append(" ( ∑");
        sb.append(DecimalFormatter.to2Decimal(circadianPercentageProfilePlugin.percentageBasalSum()));
        sb.append("U )");
        sb.append("</h4> " + circadianPercentageProfilePlugin.basalString());
        sb.append("<h4>");
        sb.append(getString(R.string.nsprofileview_ic_label));
        sb.append("</h4> " + circadianPercentageProfilePlugin.icString());
        sb.append("<h4>");
        sb.append(getString(R.string.nsprofileview_isf_label));
        sb.append("</h4> " + circadianPercentageProfilePlugin.isfString());
        profileView.setText(Html.fromHtml(sb.toString()));

        baseprofileBasal.setText(Html.fromHtml("<h3>" + getString(R.string.base_profile_label) + " ( ∑" + DecimalFormatter.to2Decimal(circadianPercentageProfilePlugin.baseBasalSum()) + "U )</h3>" +
                "<h4>" + getString(R.string.nsprofileview_basal_label) + "</h4>" + circadianPercentageProfilePlugin.baseBasalString()));
        baseprofileIC.setText(Html.fromHtml("<h4>" + getString(R.string.nsprofileview_ic_label) + "</h4>" + circadianPercentageProfilePlugin.baseIcString()));
        baseprofileISF.setText(Html.fromHtml("<h4>" + getString(R.string.nsprofileview_isf_label) + "</h4>" + circadianPercentageProfilePlugin.baseIsfString()));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (basalEditDialog != null && basalEditDialog.isVisible()) {
            basalEditDialog.dismiss();
        }
        basalEditDialog = null;
        fl.requestFocusFromTouch();
    }

    public static class BasalEditDialog extends DialogFragment {

        private double[] values;
        private String title;
        private CircadianPercentageProfileFragment fragment;

        public void setup(double[] values, String title, CircadianPercentageProfileFragment fragment) {
            this.values = values;
            this.title = title;
            this.fragment = fragment;
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            getDialog().setTitle(title);
            View view = inflater.inflate(R.layout.circadianpercentageprofile_editbasal_dialog, container, false);
            LinearLayout list = (LinearLayout) view.findViewById(R.id.circadianpp_editbasal_listlayout);
            final EditText[] editTexts = new EditText[24];
            for (int i = 0; i < 24; i++) {
                View childview = inflater.inflate(R.layout.circadianpercentageprofile_listelement, container, false);
                ((TextView) childview.findViewById(R.id.basal_time_elem)).setText((i < 10 ? "0" : "") + i + ":00: ");

                ImageView copyprevbutton = (ImageView) childview.findViewById(R.id.basal_copyprev_elem);

                if (i == 0) {
                    copyprevbutton.setVisibility(View.INVISIBLE);
                } else {
                    final int j = i; //needs to be final to be passed to inner class.
                    copyprevbutton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            editTexts[j].setText(editTexts[j - 1].getText());
                        }
                    });
                }

                editTexts[i] = ((EditText) childview.findViewById(R.id.basal_edittext_elem));
                editTexts[i].setText(DecimalFormatter.to2Decimal(values[i]));
                list.addView(childview);
            }
            getDialog().setCancelable(true);

            view.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    for (int i = 0; i < 24; i++) {
                        if (editTexts[i].getText().length() != 0) {
                            values[i] = SafeParse.stringToDouble(editTexts[i].getText().toString());
                        }
                    }
                    fragment.updateProfileInfo();
                    getPlugin().storeSettings();
                    dismiss();
                }
            });

            view.findViewById(R.id.cancel_action).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismiss();
                }
            });

            return view;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (basalEditDialog != null && basalEditDialog.isVisible()) {
            basalEditDialog.dismiss();
        }
        basalEditDialog = null;

        fl.requestFocusFromTouch();
    }

    @Override
    public void onResume() {
        super.onResume();
        onStatusEvent(new EventInitializationChanged());
        fl.requestFocusFromTouch();
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!ConfigBuilderPlugin.getActivePump().isInitialized() || ConfigBuilderPlugin.getActivePump().isSuspended()) {
                        profileswitchButton.setVisibility(View.GONE);
                    } else {
                        profileswitchButton.setVisibility(View.VISIBLE);
                    }
                }
            });
    }

    @Subscribe
    public void onStatusEvent(final EventProfileSwitchChange e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
    }

}
