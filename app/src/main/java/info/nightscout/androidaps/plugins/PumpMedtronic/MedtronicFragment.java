package info.nightscout.androidaps.plugins.PumpMedtronic;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.joanzapata.iconify.widget.IconTextView;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCommon.dialog.RileylinkSettingsActivity;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.PumpDanaR.Dialogs.ProfileViewDialog;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicDeviceStatusChange;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SetWarnColor;

public class MedtronicFragment extends SubscriberFragment {
    private static Logger LOG = LoggerFactory.getLogger(MedtronicFragment.class);

    private Handler loopHandler = new Handler();
    private Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            updateGUI();
            loopHandler.postDelayed(refreshLoop, 60 * 1000L);
        }
    };

    @BindView(R.id.medtronic_lastconnection)
    TextView lastConnectionView;

    //@BindView(R.id.medtronic_btconnection)
    //TextView btConnectionView;

    @BindView(R.id.medtronic_lastbolus)
    TextView lastBolusView;

    @BindView(R.id.medtronic_basabasalrate)
    TextView basaBasalRateView;

    @BindView(R.id.medtronic_tempbasal)
    TextView tempBasalView;

    @BindView(R.id.medtronic_pumpstate_battery)
    TextView batteryView;

    @BindView(R.id.medtronic_rl_status)
    IconTextView rileyLinkStatus;

    @BindView(R.id.medtronic_reservoir)
    TextView reservoirView;

    @BindView(R.id.medtronic_errors)
    TextView errorsView;


    @BindView(R.id.medtronic_queue)
    TextView queueView;

    @BindView(R.id.overview_pumpstatuslayout)
    LinearLayout pumpStatusLayout;

    @BindView(R.id.overview_pump_medtronic)
    TextView overviewPumpMedtronicView;

    @BindView(R.id.medtronic_pump_status)
    IconTextView pumpStatusIconView;


    public MedtronicFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loopHandler.postDelayed(refreshLoop, 60 * 1000L);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        loopHandler.removeCallbacks(refreshLoop);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.medtronic_fragment, container, false);
            unbinder = ButterKnife.bind(this, view);

            overviewPumpMedtronicView.setBackgroundColor(MainApp.sResources.getColor(R.color.colorInitializingBorder));

            rileyLinkStatus.setText(getTranslation(RileyLinkServiceState.NotStarted.getResourceId(getTargetDevice())));
            rileyLinkStatus.setTextSize(14);

            pumpStatusIconView.setTextColor(Color.WHITE);
            pumpStatusIconView.setTextSize(14);
            pumpStatusIconView.setText("{fa-bed}");

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @OnClick(R.id.medtronic_history)
    void onHistoryClick() {
        startActivity(new Intent(getContext(), DanaRHistoryActivity.class));
    }

    @OnClick(R.id.medtronic_viewprofile)
    void onViewProfileClick() {
        FragmentManager manager = getFragmentManager();
        ProfileViewDialog profileViewDialog = new ProfileViewDialog();
        profileViewDialog.show(manager, "ProfileViewDialog");
    }

    @OnClick(R.id.medtronic_stats)
    void onStatsClick() {
        startActivity(new Intent(getContext(), RileylinkSettingsActivity.class));
    }

    /*@OnClick(R.id.medtronic_btconnection)
    void onBtConnectionClick() {
        log.debug("Clicked connect to pump");
        DanaRPump.getInstance().lastConnection = 0;
        ConfigBuilderPlugin.getCommandQueue().readStatus("Clicked connect to pump", null);
    }*/

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged c) {

    }


    @Subscribe
    public void onStatusEvent(final EventMedtronicDeviceStatusChange eventStatusChange) {
        LOG.info("onStatusEvent(EventMedtronicDeviceStatusChange): {}", eventStatusChange);
        Activity activity = getActivity();
        //final String status = c.textStatus();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {

                            MedtronicPumpStatus pumpStatus = MedtronicUtil.getPumpStatus();

                            if (eventStatusChange.rileyLinkServiceState != null)
                                pumpStatus.rileyLinkServiceState = eventStatusChange.rileyLinkServiceState;

                            if (eventStatusChange.rileyLinkError != null)
                                pumpStatus.rileyLinkError = eventStatusChange.rileyLinkError;

                            if (eventStatusChange.pumpDeviceState != null)
                                pumpStatus.pumpDeviceState = eventStatusChange.pumpDeviceState;

                            setDeviceStatus(pumpStatus);
                            //pumpStatusIconView.setTextColor(Color.WHITE);
                            //pumpStatusIconView.setTextSize(20);
                            //pumpStatusIconView.setText("{fa-bed}");
                        }
                    }
            );
        }

    }


    private void setDeviceStatus(MedtronicPumpStatus pumpStatus) {
        if (pumpStatus.rileyLinkServiceState != null) {

            int resourceId = pumpStatus.rileyLinkServiceState.getResourceId(getTargetDevice());
            rileyLinkStatus.setTextColor(Color.WHITE);
            rileyLinkStatus.setTextSize(14);

            if (pumpStatus.rileyLinkServiceState == RileyLinkServiceState.NotStarted) {
                rileyLinkStatus.setText("  " + getTranslation(resourceId));
                rileyLinkStatus.setTextSize(14);
            } else if (pumpStatus.rileyLinkServiceState.isConnecting()) {
                rileyLinkStatus.setText("{fa-bluetooth-b spin}   " + getTranslation(resourceId));
            } else if (pumpStatus.rileyLinkServiceState.isError()) {
                rileyLinkStatus.setText("{fa-bluetooth-b}   " + getTranslation(resourceId));
                rileyLinkStatus.setTextColor(Color.RED);
            } else {
                rileyLinkStatus.setText("{fa-bluetooth-b}   " + getTranslation(resourceId));
            }
        }

        if (pumpStatus.rileyLinkError != null) {
            int resourceId = pumpStatus.rileyLinkError.getResourceId(getTargetDevice());
            errorsView.setText(getTranslation(resourceId));
        }

        if (pumpStatus.pumpDeviceState != null) {
            // TODO  Pump State

            switch (pumpStatus.pumpDeviceState) {
                case Sleeping:
                    pumpStatusIconView.setText("{fa-bed}   " + pumpStatus.pumpDeviceState.name());
                    break;

                case NeverContacted:
                case WakingUp:
                case ProblemContacting:
                case InvalidConfiguration:
                    pumpStatusIconView.setText("   " + pumpStatus.pumpDeviceState.name());
                    break;

                // FIXME
                case Active:
                    pumpStatusIconView.setText("   " + pumpStatus.pumpDeviceState.name());
                    break;

                // FIXME
                case ErrorWhenCommunicating:
                    pumpStatusIconView.setText("   " + pumpStatus.pumpDeviceState.name());
                    break;

                // FIXME
                case TimeoutWhenCommunicating:
                    pumpStatusIconView.setText("   " + pumpStatus.pumpDeviceState.name());
                    break;
            }
        }


        if (queueView != null) {
            // FIXME
            //queueView.setVisibility(View.GONE);

            Spanned status = ConfigBuilderPlugin.getCommandQueue().spannedStatus();
            if (status.toString().equals("")) {
                queueView.setVisibility(View.GONE);
            } else {
                queueView.setVisibility(View.VISIBLE);
                queueView.setText(status);
            }
        }


    }


    public RileyLinkTargetDevice getTargetDevice() {
        return RileyLinkTargetDevice.MedtronicPump;
    }


    public String getTranslation(int resourceId) {
        return MainApp.gs(resourceId);
    }


    @Subscribe
    public void onStatusEvent(final EventMedtronicPumpValuesChanged s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventQueueChanged s) {
        updateGUI();
    }

    // GUI functions
    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {

                    MedtronicPumpPlugin plugin = (MedtronicPumpPlugin) MedtronicPumpPlugin.getPlugin();
                    MedtronicPumpStatus pumpStatus = MedtronicUtil.getPumpStatus();

                    setDeviceStatus(pumpStatus);

                    if (pumpStatus.lastConnection != 0) {
                        Long agoMsec = System.currentTimeMillis() - pumpStatus.lastConnection;
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        lastConnectionView.setText(DateUtil.timeString(pumpStatus.lastConnection) + " (" + String.format(MainApp.sResources.getString(R.string.minago), agoMin) + ")");
                        SetWarnColor.setColor(lastConnectionView, agoMin, 16d, 31d);
                    }


                    // last bolus
                    Double bolus = pumpStatus.lastBolusAmount;
                    Date bolusTime = pumpStatus.lastBolusTime;
                    if (bolus != null && bolusTime != null) {
                        long agoMsc = System.currentTimeMillis() - pumpStatus.lastBolusTime.getTime();
                        double bolusMinAgo = agoMsc / 60d / 1000d;
                        String unit = MainApp.gs(R.string.insulin_unit_shortname);
                        String ago;
                        if ((agoMsc < 60 * 1000)) {
                            ago = MainApp.gs(R.string.combo_pump_connected_now);
                        } else if (bolusMinAgo < 60) {
                            ago = DateUtil.minAgo(pumpStatus.lastBolusTime.getTime());
                        } else {
                            ago = DateUtil.hourAgo(pumpStatus.lastBolusTime.getTime());
                        }
                        lastBolusView.setText(MainApp.gs(R.string.combo_last_bolus, bolus, unit, ago));
                    } else {
                        lastBolusView.setText("");
                    }

                    //dailyUnitsView.setText(DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U");
                    //SetWarnColor.setColor(dailyUnitsView, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75d, pump.maxDailyTotalUnits * 0.9d);
                    basaBasalRateView.setText("(" + (pumpStatus.activeProfileName) + ")  " + DecimalFormatter.to2Decimal(ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) + " U/h");

                    if (ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses()) {
                        if (TreatmentsPlugin.getPlugin().isInHistoryRealTempBasalInProgress()) {
                            tempBasalView.setText(TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tempBasalView.setText("");
                        }
                    } else {
                        // v2 plugin
                        if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                            tempBasalView.setText(TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tempBasalView.setText("");
                        }
                    }

                    reservoirView.setText(DecimalFormatter.to0Decimal(pumpStatus.reservoirRemainingUnits) + " / " + pumpStatus.reservoirFullUnits + " U");
                    SetWarnColor.setColorInverse(reservoirView, pumpStatus.reservoirRemainingUnits, 50d, 20d);
                    batteryView.setText("{fa-battery-" + (pumpStatus.batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(batteryView, pumpStatus.batteryRemaining, 51d, 26d);
                    //iobView.setText(pump.iob + " U");


                    errorsView.setText(pumpStatus.getErrorInfo());

                }
            });
    }

}
