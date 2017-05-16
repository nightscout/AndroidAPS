package info.nightscout.androidaps.plugins.PumpDanaRKorean;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.PumpDanaR.Dialogs.ProfileViewDialog;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.History.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.History.DanaRStatsActivity;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SetWarnColor;

public class DanaRKoreanFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(DanaRKoreanFragment.class);

    private static DanaRKoreanPlugin danaRKoreanPlugin = new DanaRKoreanPlugin();

    public static DanaRKoreanPlugin getPlugin() {
        return danaRKoreanPlugin;
    }

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;

    private Handler loopHandler = new Handler();
    private Runnable refreshLoop = null;

    TextView lastConnectionView;
    TextView btConnectionView;
    TextView lastBolusView;
    TextView dailyUnitsView;
    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;
    TextView iobView;
    TextView firmwareView;
    TextView basalStepView;
    TextView bolusStepView;
    Button viewProfileButton;
    Button historyButton;
    Button statsButton;


    public DanaRKoreanFragment() {
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(DanaRKoreanFragment.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (refreshLoop == null) {
            refreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    loopHandler.postDelayed(refreshLoop, 60 * 1000L);
                }
            };
            loopHandler.postDelayed(refreshLoop, 60 * 1000L);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.danar_fragment, container, false);
        btConnectionView = (TextView) view.findViewById(R.id.danar_btconnection);
        lastConnectionView = (TextView) view.findViewById(R.id.danar_lastconnection);
        lastBolusView = (TextView) view.findViewById(R.id.danar_lastbolus);
        dailyUnitsView = (TextView) view.findViewById(R.id.danar_dailyunits);
        basaBasalRateView = (TextView) view.findViewById(R.id.danar_basabasalrate);
        tempBasalView = (TextView) view.findViewById(R.id.danar_tempbasal);
        extendedBolusView = (TextView) view.findViewById(R.id.danar_extendedbolus);
        batteryView = (TextView) view.findViewById(R.id.danar_battery);
        reservoirView = (TextView) view.findViewById(R.id.danar_reservoir);
        iobView = (TextView) view.findViewById(R.id.danar_iob);
        firmwareView = (TextView) view.findViewById(R.id.danar_firmware);
        viewProfileButton = (Button) view.findViewById(R.id.danar_viewprofile);
        historyButton = (Button) view.findViewById(R.id.danar_history);
        statsButton = (Button) view.findViewById(R.id.danar_stats);
        basalStepView = (TextView) view.findViewById(R.id.danar_basalstep);
        bolusStepView = (TextView) view.findViewById(R.id.danar_bolusstep);


        viewProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager manager = getFragmentManager();
                ProfileViewDialog profileViewDialog = new ProfileViewDialog();
                profileViewDialog.show(manager, "ProfileViewDialog");
            }
        });

        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getContext(), DanaRHistoryActivity.class));
            }
        });

        statsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getContext(), DanaRStatsActivity.class));
            }
        });

        btConnectionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sHandler.post(new Runnable() {
                                  @Override
                                  public void run() {
                                      DanaRKoreanPlugin.sExecutionService.connect("Connect request from GUI");
                                  }
                              }
                );
            }
        });

        updateGUI();
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged c) {
        Activity activity = getActivity();
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

    // GUI functions
    private void updateGUI() {

        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {
                    DanaRKoreanPump pump = DanaRKoreanPlugin.getDanaRPump();
                    if (pump.lastConnection.getTime() != 0) {
                        Long agoMsec = new Date().getTime() - pump.lastConnection.getTime();
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        lastConnectionView.setText(DateUtil.timeString(pump.lastConnection) + " (" + String.format(MainApp.sResources.getString(R.string.minago), agoMin) + ")");
                        SetWarnColor.setColor(lastConnectionView, agoMin, 16d, 31d);
                    }
//                    if (pump.lastBolusTime.getTime() != 0) {
//                        Long agoMsec = new Date().getTime() - pump.lastBolusTime.getTime();
//                        double agoHours =  agoMsec / 60d / 60d / 1000d;
//                        if (agoHours < 6) // max 6h back
//                            lastBolusView.setText(formatTime.format(pump.lastBolusTime) + " (" + DecimalFormatter.to1Decimal(agoHours) + " " + getString(R.string.hoursago) + ") " + DecimalFormatter.to2Decimal(pump.lastBolusAmount) + " U");
//                        else lastBolusView.setText("");
//                    }

                    dailyUnitsView.setText(DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U");
                    SetWarnColor.setColor(dailyUnitsView, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75d, pump.maxDailyTotalUnits * 0.9d);
                    basaBasalRateView.setText("( " + (pump.activeProfile + 1) + " )  " + DecimalFormatter.to2Decimal(danaRKoreanPlugin.getBaseBasalRate()) + " U/h");
                    if (danaRKoreanPlugin.isRealTempBasalInProgress()) {
                        tempBasalView.setText(danaRKoreanPlugin.getRealTempBasal().toString());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (danaRKoreanPlugin.isExtendedBoluslInProgress()) {
                        extendedBolusView.setText(danaRKoreanPlugin.getExtendedBolus().toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    reservoirView.setText(DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + " / 300 U");
                    SetWarnColor.setColorInverse(reservoirView, pump.reservoirRemainingUnits, 50d, 20d);
                    batteryView.setText("{fa-battery-" + (pump.batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(batteryView, pump.batteryRemaining, 51d, 26d);
                    iobView.setText(pump.iob + " U");
                    if (pump.isNewPump) {
                        firmwareView.setText(String.format(MainApp.sResources.getString(R.string.danar_model), pump.model, pump.protocol, pump.productCode));
                    } else {
                        firmwareView.setText("OLD");
                    }
                    basalStepView.setText("" + pump.basalStep);
                    bolusStepView.setText("" + pump.bolusStep);
                }
            });

    }

}
