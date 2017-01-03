package info.nightscout.androidaps.plugins.DanaR;


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
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.DanaR.Dialogs.ProfileViewDialog;
import info.nightscout.androidaps.plugins.DanaR.History.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRNewStatus;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SetWarnColor;

public class DanaRFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(DanaRFragment.class);

    private static DanaRPlugin danaRPlugin;

    public static DanaRPlugin getPlugin() {
        if(danaRPlugin==null){
            danaRPlugin = new DanaRPlugin();
        }
        return danaRPlugin;
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
    Button viewProfileButton;
    Button historyButton;

    public DanaRFragment() {
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(DanaRFragment.class.getSimpleName());
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
        viewProfileButton = (Button) view.findViewById(R.id.danar_viewprofile);
        historyButton = (Button) view.findViewById(R.id.danar_history);

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

        btConnectionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sHandler.post(new Runnable() {
                                  @Override
                                  public void run() {
                                      DanaRPlugin.sExecutionService.connect("Connect request from GUI");
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
    public void onStatusEvent(final EventDanaRConnectionStatus c) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (c.sStatus == EventDanaRConnectionStatus.CONNECTING)
                                btConnectionView.setText("{fa-bluetooth-b spin} " + c.sSecondsElapsed + "s");
                            else if (c.sStatus == EventDanaRConnectionStatus.CONNECTED)
                                btConnectionView.setText("{fa-bluetooth}");
                            else
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

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
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

                    if (DanaRPlugin.getDanaRPump().lastConnection.getTime() != 0) {
                        Long agoMsec = new Date().getTime() - DanaRPlugin.getDanaRPump().lastConnection.getTime();
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        lastConnectionView.setText(DateUtil.timeString(DanaRPlugin.getDanaRPump().lastConnection) + " (" + String.format(MainApp.sResources.getString(R.string.minago), agoMin) + ")");
                        SetWarnColor.setColor(lastConnectionView, agoMin, 16d, 31d);
                    }
                    if (DanaRPlugin.getDanaRPump().lastBolusTime.getTime() != 0) {
                        Long agoMsec = new Date().getTime() - DanaRPlugin.getDanaRPump().lastBolusTime.getTime();
                        double agoHours =  agoMsec / 60d / 60d / 1000d;
                        if (agoHours < 6) // max 6h back
                            lastBolusView.setText(DateUtil.timeString(DanaRPlugin.getDanaRPump().lastBolusTime) + " (" + DecimalFormatter.to1Decimal(agoHours) + " " + getString(R.string.hoursago) + ") " + DecimalFormatter.to2Decimal(getPlugin().getDanaRPump().lastBolusAmount) + " U");
                        else lastBolusView.setText("");
                    }

                    dailyUnitsView.setText(DecimalFormatter.to0Decimal(DanaRPlugin.getDanaRPump().dailyTotalUnits) + " / " + DanaRPlugin.getDanaRPump().maxDailyTotalUnits + " U");
                    SetWarnColor.setColor(dailyUnitsView, DanaRPlugin.getDanaRPump().dailyTotalUnits, DanaRPlugin.getDanaRPump().maxDailyTotalUnits * 0.75d, DanaRPlugin.getDanaRPump().maxDailyTotalUnits * 0.9d);
                    basaBasalRateView.setText("( " + (DanaRPlugin.getDanaRPump().activeProfile + 1) + " )  " + DecimalFormatter.to2Decimal(getPlugin().getBaseBasalRate()) + " U/h");
                    if (getPlugin().isRealTempBasalInProgress()) {
                        tempBasalView.setText(getPlugin().getRealTempBasal().toString());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (getPlugin().isExtendedBoluslInProgress()) {
                        extendedBolusView.setText(getPlugin().getExtendedBolus().toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    reservoirView.setText(DecimalFormatter.to0Decimal(DanaRPlugin.getDanaRPump().reservoirRemainingUnits) + " / 300 U");
                    SetWarnColor.setColorInverse(reservoirView, DanaRPlugin.getDanaRPump().reservoirRemainingUnits, 50d, 20d);
                    batteryView.setText("{fa-battery-" + (DanaRPlugin.getDanaRPump().batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(batteryView, DanaRPlugin.getDanaRPump().batteryRemaining, 51d, 26d);
                    iobView.setText(DanaRPlugin.getDanaRPump().iob + " U");
                }
            });

    }

}
