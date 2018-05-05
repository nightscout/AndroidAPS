package info.nightscout.androidaps.plugins.PumpDanaR;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.TDDStatsActivity;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.Dialogs.ProfileViewDialog;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.SetWarnColor;

public class DanaRFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(DanaRFragment.class);

    private Handler loopHandler = new Handler();
    private Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            updateGUI();
            loopHandler.postDelayed(refreshLoop, 60 * 1000L);
        }
    };

    @BindView(R.id.danar_lastconnection)
    TextView lastConnectionView;
    @BindView(R.id.danar_btconnection)
    TextView btConnectionView;
    @BindView(R.id.danar_lastbolus)
    TextView lastBolusView;
    @BindView(R.id.danar_dailyunits)
    TextView dailyUnitsView;
    @BindView(R.id.danar_basabasalrate)
    TextView basaBasalRateView;
    @BindView(R.id.danar_tempbasal)
    TextView tempBasalView;
    @BindView(R.id.danar_extendedbolus)
    TextView extendedBolusView;
    @BindView(R.id.danar_battery)
    TextView batteryView;
    @BindView(R.id.danar_reservoir)
    TextView reservoirView;
    @BindView(R.id.danar_iob)
    TextView iobView;
    @BindView(R.id.danar_firmware)
    TextView firmwareView;
    @BindView(R.id.danar_basalstep)
    TextView basalStepView;
    @BindView(R.id.danar_bolusstep)
    TextView bolusStepView;
    @BindView(R.id.danar_serialnumber)
    TextView serialNumberView;
    @BindView(R.id.danar_queue)
    TextView queueView;

    @BindView(R.id.overview_pumpstatuslayout)
    LinearLayout pumpStatusLayout;
    @BindView(R.id.overview_pumpstatus)
    TextView pumpStatusView;

    public DanaRFragment() {
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
            View view = inflater.inflate(R.layout.danar_fragment, container, false);
            unbinder = ButterKnife.bind(this, view);

            pumpStatusView.setBackgroundColor(MainApp.sResources.getColor(R.color.colorInitializingBorder));

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @OnClick(R.id.danar_history)
    void onHistoryClick() {
        startActivity(new Intent(getContext(), DanaRHistoryActivity.class));
    }

    @OnClick(R.id.danar_viewprofile)
    void onViewProfileClick() {
        FragmentManager manager = getFragmentManager();
        ProfileViewDialog profileViewDialog = new ProfileViewDialog();
        profileViewDialog.show(manager, "ProfileViewDialog");
    }

    @OnClick(R.id.danar_stats)
    void onStatsClick() {
        startActivity(new Intent(getContext(), TDDStatsActivity.class));
    }

    @OnClick(R.id.danar_btconnection)
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
                    DanaRPump pump = DanaRPump.getInstance();
                    if (pump.lastConnection != 0) {
                        Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        lastConnectionView.setText(DateUtil.timeString(pump.lastConnection) + " (" + String.format(MainApp.gs(R.string.minago), agoMin) + ")");
                        SetWarnColor.setColor(lastConnectionView, agoMin, 16d, 31d);
                    }
                    if (pump.lastBolusTime.getTime() != 0) {
                        Long agoMsec = System.currentTimeMillis() - pump.lastBolusTime.getTime();
                        double agoHours = agoMsec / 60d / 60d / 1000d;
                        if (agoHours < 6) // max 6h back
                            lastBolusView.setText(DateUtil.timeString(pump.lastBolusTime) + " " + DateUtil.sinceString(pump.lastBolusTime.getTime()) + " " + DecimalFormatter.to2Decimal(DanaRPump.getInstance().lastBolusAmount) + " U");
                        else lastBolusView.setText("");
                    }

                    dailyUnitsView.setText(DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U");
                    SetWarnColor.setColor(dailyUnitsView, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75d, pump.maxDailyTotalUnits * 0.9d);
                    basaBasalRateView.setText("( " + (pump.activeProfile + 1) + " )  " + DecimalFormatter.to2Decimal(ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) + " U/h");
                    // DanaRPlugin, DanaRKoreanPlugin
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
                    ExtendedBolus activeExtendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
                    if (activeExtendedBolus != null) {
                        extendedBolusView.setText(activeExtendedBolus.toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    reservoirView.setText(DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + " / 300 U");
                    SetWarnColor.setColorInverse(reservoirView, pump.reservoirRemainingUnits, 50d, 20d);
                    batteryView.setText("{fa-battery-" + (pump.batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(batteryView, pump.batteryRemaining, 51d, 26d);
                    iobView.setText(pump.iob + " U");
                    if (pump.model != 0 || pump.protocol != 0 || pump.productCode != 0) {
                        firmwareView.setText(String.format(MainApp.gs(R.string.danar_model), pump.model, pump.protocol, pump.productCode));
                    } else {
                        firmwareView.setText("OLD");
                    }
                    basalStepView.setText("" + pump.basalStep);
                    bolusStepView.setText("" + pump.bolusStep);
                    serialNumberView.setText("" + pump.serialNumber);
                    if (queueView != null) {
                        Spanned status = ConfigBuilderPlugin.getCommandQueue().spannedStatus();
                        if (status.toString().equals("")) {
                            queueView.setVisibility(View.GONE);
                        } else {
                            queueView.setVisibility(View.VISIBLE);
                            queueView.setText(status);
                        }
                    }
                }
            });
    }

}
