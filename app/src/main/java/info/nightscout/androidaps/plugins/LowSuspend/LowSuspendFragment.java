package info.nightscout.androidaps.plugins.LowSuspend;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Pump;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.plugins.APSBase;
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.androidaps.plugins.PluginBase;
import info.nightscout.client.data.NSProfile;

public class LowSuspendFragment extends Fragment implements View.OnClickListener, PluginBase, APSBase {
    private static Logger log = LoggerFactory.getLogger(LowSuspendFragment.class);

    Button run;
    TextView lastRunView;
    TextView glucoseStatusView;
    TextView minBgView;
    TextView resultView;
    TextView requestView;

    Date lastAPSRun = null;
    APSResult lastAPSResult = null;


    @Override
    public int getType() {
        return PluginBase.APS;
    }

    @Override
    public boolean isFragmentVisible() {
        return true;
    }

    @Override
    public APSResult getLastAPSResult() {
        return lastAPSResult;
    }

    @Override
    public Date getLastAPSRun() {
        return lastAPSRun;
    }

    public static LowSuspendFragment newInstance() {
        LowSuspendFragment fragment = new LowSuspendFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBus();
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

        return view;
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

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    invoke();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    @Override
    public void invoke() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
        DecimalFormat formatNumber1decimalplaces = new DecimalFormat("0.0");
        NSProfile profile = MainApp.getNSProfile();
        Pump pump = MainApp.getActivePump();

        if (glucoseStatus == null) {
            resultView.setText(getString(R.string.openapsma_noglucosedata));
            if (Config.logAPSResult) log.debug(getString(R.string.openapsma_noglucosedata));
            return;
        }

        if (profile == null) {
            resultView.setText(getString(R.string.openapsma_noprofile));
            if (Config.logAPSResult) log.debug(getString(R.string.openapsma_noprofile));
            return;
        }

        if (pump == null) {
            resultView.setText(getString(R.string.openapsma_nopump));
            if (Config.logAPSResult) log.debug(getString(R.string.openapsma_nopump));
            return;
        }

        String minBgDefault = "90";
        if (!MainApp.getNSProfile().getUnits().equals(Constants.MGDL)) {
            minBgDefault = "5";
        }

        double minBg = NSProfile.toMgdl(Double.parseDouble(SP.getString("min_bg", minBgDefault).replace(",", ".")), profile.getUnits());

        boolean lowProjected = (glucoseStatus.glucose + 6.0 * glucoseStatus.avgdelta) < minBg;
        boolean low = glucoseStatus.glucose < minBg;

        APSResult request = new APSResult();
        Double baseBasalRate = pump.getBaseBasalRate();
        boolean isTempBasalInProgress = pump.isTempBasalInProgress();
        Double tempBasalRate = pump.getTempBasalAbsoluteRate();

        if (low && !lowProjected) {
            if (!isTempBasalInProgress || tempBasalRate != 0d) {
                request.changeRequested = true;
                request.rate = 0d;
                request.duration = 30;
                request.reason = getString(R.string.lowsuspend_lowmessage);
            } else {
                request.changeRequested = false;
                request.reason = getString(R.string.nochangerequested);
            }
        } else if (lowProjected) {
            if (!isTempBasalInProgress || tempBasalRate != 0d) {
                request.changeRequested = true;
                request.rate = 0d;
                request.duration = 30;
                request.reason = getString(R.string.lowsuspend_lowprojectedmessage);
            } else {
                request.changeRequested = false;
                request.reason = getString(R.string.nochangerequested);
            }
        } else if (tempBasalRate == 0d) {
            request.changeRequested = true;
            request.rate = baseBasalRate;
            request.duration = 30;
            request.reason = getString(R.string.lowsuspend_cancelmessage);
        } else {
            request.changeRequested = false;
            request.reason = getString(R.string.nochangerequested);
        }
        glucoseStatusView.setText(glucoseStatus.toString());
        minBgView.setText(formatNumber1decimalplaces.format(minBg) + " " + profile.getUnits());
        resultView.setText(getString(R.string.lowsuspend_low) + " " + low + "\n" + getString(R.string.lowsuspend_lowprojected) + " " + lowProjected);
        requestView.setText(request.toString());
        lastRunView.setText(new Date().toLocaleString());

        lastAPSResult = request;
        lastAPSRun = new Date();
    }
}
