package info.nightscout.androidaps.plugins.LowSuspend;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.client.data.NSProfile;

/**
 * LOW SUSPEND ALGORITHM
 * <p/>
 * Define projection as BG + 6 * avgdelta    (estimated BG in 30 min)
 * <p/>
 * If BG is bellow low threshold and projection too: set basal rate to 0 U/h if low temp is not running
 * else if projection is bellow low threshold: set basal rate to 0 U/h if low temp is not running
 * else if exists low temp: cancel it
 * else no change
 */

public class LowSuspendFragment extends Fragment implements View.OnClickListener, PluginBase, APSInterface {
    private static Logger log = LoggerFactory.getLogger(LowSuspendFragment.class);

    Button run;
    TextView lastRunView;
    TextView glucoseStatusView;
    TextView minBgView;
    TextView resultView;
    TextView requestView;

    // last values
    class LastRun implements Parcelable {
        public Boolean lastLow = null;
        public Boolean lastLowProjected = null;
        public Double lastMinBg = null;
        public String lastUnits = null;
        public DatabaseHelper.GlucoseStatus lastGlucoseStatus = null;
        public Date lastAPSRun = null;
        public APSResult lastAPSResult = null;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(lastLow ? 1 : 0);
            dest.writeInt(lastLowProjected ? 1 : 0);
            dest.writeDouble(lastMinBg);
            dest.writeString(lastUnits);
            dest.writeParcelable(lastGlucoseStatus, 0);
            dest.writeLong(lastAPSRun.getTime());
            dest.writeParcelable(lastAPSResult, 0);
        }

        public final Parcelable.Creator<LastRun> CREATOR = new Parcelable.Creator<LastRun>() {
            public LastRun createFromParcel(Parcel in) {
                return new LastRun(in);
            }

            public LastRun[] newArray(int size) {
                return new LastRun[size];
            }
        };

        private LastRun(Parcel in) {
            lastLow = in.readInt() == 1;
            lastLowProjected = in.readInt() == 1;
            lastMinBg = in.readDouble();
            lastUnits = in.readString();
            lastGlucoseStatus = in.readParcelable(DatabaseHelper.GlucoseStatus.class.getClassLoader());
            lastAPSRun = new Date(in.readLong());
            lastAPSResult = in.readParcelable(APSResult.class.getClassLoader());
        }

        public LastRun() {
        }
    }

    static LastRun lastRun = null;

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = true;

    public LowSuspendFragment() {
        super();
        registerBus();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.lowsuspend);
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

    @Override
    public int getType() {
        return PluginBase.APS;
    }

    @Override
    public APSResult getLastAPSResult() {
        if (lastRun != null)
            return lastRun.lastAPSResult;
        else return null;
    }

    @Override
    public Date getLastAPSRun() {
        if (lastRun != null)
            return lastRun.lastAPSRun;
        else return null;
    }

    public static LowSuspendFragment newInstance() {
        LowSuspendFragment fragment = new LowSuspendFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.lowsuspend_fragment, container, false);

        run = (Button) view.findViewById(R.id.lowsuspend_run);
        run.setOnClickListener(this);
        lastRunView = (TextView) view.findViewById(R.id.lowsuspend_lastrun);
        glucoseStatusView = (TextView) view.findViewById(R.id.lowsuspend_glucosestatus);
        minBgView = (TextView) view.findViewById(R.id.lowsuspend_minbg);
        resultView = (TextView) view.findViewById(R.id.lowsuspend_result);
        requestView = (TextView) view.findViewById(R.id.lowsuspend_request);

        if (savedInstanceState != null) {
            lastRun = savedInstanceState.getParcelable("lastrun");
        }
        updateGUI();
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("lastrun", lastRun);
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
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.lowsuspend_run:
                invoke();
                break;
        }

    }

    @Override
    public void invoke() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();
        PumpInterface pump = MainActivity.getConfigBuilder().getActivePump();

        if (!isEnabled()) {
            updateResultGUI(MainApp.instance().getString(R.string.openapsma_disabled));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            updateResultGUI(MainApp.instance().getString(R.string.openapsma_noglucosedata));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noglucosedata));
            return;
        }

        if (profile == null) {
            updateResultGUI(MainApp.instance().getString(R.string.openapsma_noprofile));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noprofile));
            return;
        }

        if (pump == null) {
            updateResultGUI(MainApp.instance().getString(R.string.openapsma_nopump));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_nopump));
            return;
        }

        String minBgDefault = "90";
        if (!profile.getUnits().equals(Constants.MGDL)) {
            minBgDefault = "5";
        }

        double minBg = NSProfile.toMgdl(Double.parseDouble(SP.getString("lowsuspend_lowthreshold", minBgDefault).replace(",", ".")), profile.getUnits());

        boolean lowProjected = (glucoseStatus.glucose + 6.0 * glucoseStatus.avgdelta) < minBg;
        boolean low = glucoseStatus.glucose < minBg;

        APSResult request = new APSResult();
        Double baseBasalRate = pump.getBaseBasalRate();
        boolean isTempBasalInProgress = pump.isTempBasalInProgress();
        Double tempBasalRate = pump.getTempBasalAbsoluteRate();
        Date now = new Date();

        if (low && !lowProjected) {
            if (!isTempBasalInProgress || tempBasalRate != 0d) {
                request.changeRequested = true;
                request.rate = 0d;
                request.duration = 30;
                request.reason = MainApp.instance().getString(R.string.lowsuspend_lowmessage);
            } else {
                request.changeRequested = false;
                request.reason = MainApp.instance().getString(R.string.nochangerequested);
            }
        } else if (lowProjected) {
            if (!isTempBasalInProgress || tempBasalRate != 0d) {
                request.changeRequested = true;
                request.rate = 0d;
                request.duration = 30;
                request.reason = MainApp.instance().getString(R.string.lowsuspend_lowprojectedmessage);
            } else {
                request.changeRequested = false;
                request.reason = MainApp.instance().getString(R.string.nochangerequested);
            }
        } else if (isTempBasalInProgress && tempBasalRate == 0d) {
            request.changeRequested = true;
            request.rate = baseBasalRate;
            request.duration = 30;
            request.reason = MainApp.instance().getString(R.string.lowsuspend_cancelmessage);
        } else {
            request.changeRequested = false;
            request.reason = MainApp.instance().getString(R.string.nochangerequested);
        }

        lastRun = new LastRun();
        lastRun.lastMinBg = minBg;
        lastRun.lastLow = low;
        lastRun.lastLowProjected = lowProjected;
        lastRun.lastGlucoseStatus = glucoseStatus;
        lastRun.lastUnits = profile.getUnits();
        lastRun.lastAPSResult = request;
        lastRun.lastAPSRun = now;
        updateGUI();
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (lastRun != null) {
                        DecimalFormat formatNumber1decimalplaces = new DecimalFormat("0.0");
                        glucoseStatusView.setText(lastRun.lastGlucoseStatus.toString());
                        minBgView.setText(formatNumber1decimalplaces.format(lastRun.lastMinBg) + " " + lastRun.lastUnits);
                        resultView.setText(getString(R.string.lowsuspend_low) + " " + lastRun.lastLow + "\n" + getString(R.string.lowsuspend_lowprojected) + " " + lastRun.lastLowProjected);
                        requestView.setText(lastRun.lastAPSResult.toString());
                        lastRunView.setText(lastRun.lastAPSRun.toLocaleString());
                    }
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    void updateResultGUI(final String text) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultView.setText(text);
                    glucoseStatusView.setText("");
                    minBgView.setText("");
                    requestView.setText("");
                    lastRunView.setText("");
                }
            });
    }
}
