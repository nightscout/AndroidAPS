package info.nightscout.androidaps.plugins.CircadianPercentageProfile;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class CircadianPercentageProfileFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(CircadianPercentageProfileFragment.class);

    private static CircadianPercentageProfilePlugin circadianPercentageProfilePlugin = new CircadianPercentageProfilePlugin();

    public static CircadianPercentageProfilePlugin getPlugin() {
        return circadianPercentageProfilePlugin;
    }

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText carView;
    EditText targetlowView;
    EditText targethighView;
    EditText percentageView;
    EditText timeshiftView;
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


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.circadianpercentageprofile_fragment, container, false);
        diaView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.circadianpercentageprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.circadianpercentageprofile_mmol);
        carView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_car);
        targetlowView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_targetlow);
        targethighView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_targethigh);
        percentageView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_percentage);
        timeshiftView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_timeshift);
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

        PumpInterface pump = MainApp.getConfigBuilder();
        if (!pump.getPumpDescription().isTempBasalCapable) {
            layout.findViewById(R.id.circadianpercentageprofile_baseprofilebasal_layout).setVisibility(View.GONE);
        }


        mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
        mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
        diaView.setText(circadianPercentageProfilePlugin.dia.toString());
        carView.setText(circadianPercentageProfilePlugin.car.toString());
        targetlowView.setText(circadianPercentageProfilePlugin.targetLow.toString());
        targethighView.setText(circadianPercentageProfilePlugin.targetHigh.toString());
        percentageView.setText("" + circadianPercentageProfilePlugin.percentage);
        timeshiftView.setText("" + circadianPercentageProfilePlugin.timeshift);
        updateProfileInfo();

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
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
            }
        });

        timeshiftView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b)
                    ToastUtils.showToastInUiThread(getContext(), getString(R.string.timeshift_hint));

            }
        });

        percentageView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b)
                    ToastUtils.showToastInUiThread(getContext(), getString(R.string.percentagefactor_hint));
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

                if (SafeParse.stringToInt(percentageView.getText().toString()) == 0) {
                    circadianPercentageProfilePlugin.percentage = 100;
                } else {
                    circadianPercentageProfilePlugin.percentage = SafeParse.stringToInt(percentageView.getText().toString());
                }
                circadianPercentageProfilePlugin.dia = SafeParse.stringToDouble(diaView.getText().toString());
                circadianPercentageProfilePlugin.car = SafeParse.stringToDouble(carView.getText().toString());
                circadianPercentageProfilePlugin.targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                circadianPercentageProfilePlugin.targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                circadianPercentageProfilePlugin.timeshift = SafeParse.stringToInt(timeshiftView.getText().toString());
                circadianPercentageProfilePlugin.storeSettings();
                updateProfileInfo();
            }
        };

        diaView.addTextChangedListener(textWatch);
        carView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);
        percentageView.addTextChangedListener(textWatch);
        timeshiftView.addTextChangedListener(textWatch);

        onStatusEvent(null);

        return layout;
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
                    ;
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

        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        onStatusEvent(null);
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!MainApp.getConfigBuilder().isInitialized() || !MainApp.getConfigBuilder().getPumpDescription().isSetBasalProfileCapable) {
                        profileswitchButton.setVisibility(View.GONE);
                    } else {
                        profileswitchButton.setVisibility(View.VISIBLE);
                    }
                }
            });
    }
}
