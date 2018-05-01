package info.nightscout.androidaps.plugins.PumpMedtronic;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.Dialogs.ProfileViewDialog;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRStatsActivity;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.medtronic.MedtronicPumpStatus;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SetWarnColor;

public class MedtronicFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(MedtronicFragment.class);

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

    @BindView(R.id.medtronic_btconnection)
    TextView btConnectionView;

    @BindView(R.id.medtronic_lastbolus)
    TextView lastBolusView;

    @BindView(R.id.medtronic_basabasalrate)
    TextView basaBasalRateView;

    @BindView(R.id.medtronic_tempbasal)
    TextView tempBasalView;

    @BindView(R.id.medtronic_battery)
    TextView batteryView;

    @BindView(R.id.medtronic_reservoir)
    TextView reservoirView;

    @BindView(R.id.medtronic_iob)
    TextView iobView;

    @BindView(R.id.medtronic_errors)
    TextView errorsView;



    @BindView(R.id.medtronic_queue)
    TextView queueView;

    @BindView(R.id.overview_pumpstatuslayout)
    LinearLayout pumpStatusLayout;

    @BindView(R.id.overview_pumpstatus) TextView pumpStatusView;

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

            pumpStatusView.setBackgroundColor(MainApp.sResources.getColor(R.color.colorInitializingBorder));

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
        startActivity(new Intent(getContext(), DanaRStatsActivity.class));
    }

    @OnClick(R.id.medtronic_btconnection)
    void onBtConnectionClick() {
        log.debug("Clicked connect to pump");
        DanaRPump.getInstance().lastConnection = 0;
        ConfigBuilderPlugin.getCommandQueue().readStatus("Clicked connect to pump", null);
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged c) {
        Activity activity = getActivity();
        final String status = c.textStatus();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (c.sStatus == EventPumpStatusChanged.CONNECTING)
                                btConnectionView.setText("{fa-bluetooth-b spin} " + c.sSecondsElapsed + "s");
                            else if (c.sStatus == EventPumpStatusChanged.CONNECTED)
                                btConnectionView.setText("{fa-bluetooth}");
                            else if (c.sStatus == EventPumpStatusChanged.DISCONNECTED)
                                btConnectionView.setText("{fa-bluetooth-b}");

                            if (!status.equals("")) {
                                pumpStatusView.setText(status);
                                pumpStatusLayout.setVisibility(View.VISIBLE);
                            } else {
                                pumpStatusLayout.setVisibility(View.GONE);
                            }
                        }
                    }
            );
        }
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRNewStatus s) {
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

                    MedtronicPumpPlugin plugin = (MedtronicPumpPlugin)MedtronicPumpPlugin.getPlugin();
                    PumpStatus pump = plugin.getPumpStatusData();

                    if (pump.lastConnection != 0) {
                        Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        lastConnectionView.setText(DateUtil.timeString(pump.lastConnection) + " (" + String.format(MainApp.sResources.getString(R.string.minago), agoMin) + ")");
                        SetWarnColor.setColor(lastConnectionView, agoMin, 16d, 31d);
                    }
                    if (pump.lastBolusTime!=null && pump.lastBolusTime.getTime() != 0) {
                        Long agoMsec = System.currentTimeMillis() - pump.lastBolusTime.getTime();
                        double agoHours = agoMsec / 60d / 60d / 1000d;
                        if (agoHours < 6) // max 6h back
                            lastBolusView.setText(DateUtil.timeString(pump.lastBolusTime) + " " + DateUtil.sinceString(pump.lastBolusTime.getTime()) + " " + DecimalFormatter.to2Decimal(DanaRPump.getInstance().lastBolusAmount) + " U");
                        else
                            lastBolusView.setText("");
                    }

                    //dailyUnitsView.setText(DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U");
                    //SetWarnColor.setColor(dailyUnitsView, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75d, pump.maxDailyTotalUnits * 0.9d);
                    basaBasalRateView.setText("(" + (pump.activeProfile) + ")  " + DecimalFormatter.to2Decimal(ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) + " U/h");

                    if (ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses()) {
                        if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
                            tempBasalView.setText(MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tempBasalView.setText("");
                        }
                    } else {
                        // v2 plugin
                        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                            tempBasalView.setText(MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tempBasalView.setText("");
                        }
                    }

                    reservoirView.setText(DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + " / " + pump.reservoirFullUnits + " U");
                    SetWarnColor.setColorInverse(reservoirView, pump.reservoirRemainingUnits, 50d, 20d);
                    batteryView.setText("{fa-battery-" + (pump.batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(batteryView, pump.batteryRemaining, 51d, 26d);
                    iobView.setText(pump.iob + " U");

                    //if (pump.isNewPump) {
                    //    firmwareView.setText(String.format(MainApp.sResources.getString(R.string.danar_model), pump.model, pump.protocol, pump.productCode));
                    //} else {
                    //    firmwareView.setText("OLD");
                    //}


                    if (queueView != null) {
                        // FIXME
                        queueView.setVisibility(View.GONE);

//                        Spanned status = ConfigBuilderPlugin.getCommandQueue().spannedStatus();
//                        if (status.toString().equals("")) {
//                            queueView.setVisibility(View.GONE);
//                        } else {
//                            queueView.setVisibility(View.VISIBLE);
//                            queueView.setText(status);
//                        }
                    }


                    errorsView.setText(pump.getErrorInfo());


                }
            });
    }

}
