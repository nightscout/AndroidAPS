package info.nightscout.androidaps.plugins.DanaR;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.client.data.NSProfile;

public class DanaRFragment extends Fragment implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(DanaRFragment.class);

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    private static DanaConnection sDanaConnection = null;
    private static DanaRPump sDanaRPump = new DanaRPump();
    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;
    boolean visibleNow = false;

    TextView connectionText;

    public static DanaConnection getDanaConnection() {
        return sDanaConnection;
    }

    public static void setDanaConnection(DanaConnection con) {
        sDanaConnection = con;
    }

    public static DanaRPump getDanaRPump() {
        return sDanaRPump;
    }

    public DanaRFragment() {
        mHandlerThread = new HandlerThread(DanaRFragment.class.getSimpleName());
        mHandlerThread.start();

        this.mHandler = new Handler(mHandlerThread.getLooper());
        registerBus();
    }

    public static DanaRFragment newInstance() {
        DanaRFragment fragment = new DanaRFragment();
        return fragment;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.danar_fragment, container, false);
        connectionText =         (TextView) view.findViewById(R.id.danar_connection);
        connectionText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (getDanaConnection() != null)
                            getDanaConnection().connectIfNotConnected("Connect request from GUI");
                        else
                            log.error("Connect req from GUI: getDanaConnection() is null");
                    }}
                );
            }
        });
        return view;
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRConnectionStatus c) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                                       @Override
                                       public void run() {
                                           if (c.sConnecting) {
                                               connectionText.setText("{fa-bluetooth-b spin} " + c.sConnectionAttemptNo);
                                           } else {
                                               if (c.sConnected) {
                                                   connectionText.setText("{fa-bluetooth}");
                                               } else {
                                                   connectionText.setText("{fa-bluetooth-b}");
                                               }
                                           }
                                       }
                                   }
            );
        }
    }

    // Plugin base interface
    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.danarpump);
    }

    @Override
    public boolean isEnabled() {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs() {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    // Pump interface
    @Override
    public boolean isTempBasalInProgress() {
        return false;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return false;
    }

    @Override
    public Integer getBatteryPercent() {
        return null;
    }

    @Override
    public Integer getReservoirValue() {
        return null;
    }

    @Override
    public void setNewBasalProfile(NSProfile profile) {

    }

    @Override
    public double getBaseBasalRate() {
        return 0;
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        return 0;
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        return 0;
    }

    @Override
    public TempBasal getTempBasal() {
        return null;
    }

    @Override
    public TempBasal getExtendedBolus() {
        return null;
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs) {
        return null;
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        return null;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        return null;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return null;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        return null;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return null;
    }

    @Override
    public JSONObject getJSONStatus() {
        return null;
    }

    @Override
    public String deviceID() {
        return null;
    }

    private void updateGUI() {
    }
}
