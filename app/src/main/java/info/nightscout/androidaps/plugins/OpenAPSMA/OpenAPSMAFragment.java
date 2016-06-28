package info.nightscout.androidaps.plugins.OpenAPSMA;

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

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;

public class OpenAPSMAFragment extends Fragment implements View.OnClickListener, PluginBase, APSInterface {
    private static Logger log = LoggerFactory.getLogger(OpenAPSMAFragment.class);

    Button run;
    TextView lastRunView;
    TextView glucoseStatusView;
    TextView currentTempView;
    TextView iobDataView;
    TextView profileView;
    TextView mealDataView;
    TextView resultView;
    TextView requestView;

    // last values
    class LastRun implements Parcelable {
        DetermineBasalAdapterJS lastDetermineBasalAdapterJS = null;
        Date lastAPSRun = null;
        DetermineBasalResult lastAPSResult = null;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(lastDetermineBasalAdapterJS, 0);
            dest.writeParcelable(lastAPSResult, 0);
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
            lastDetermineBasalAdapterJS = in.readParcelable(DetermineBasalAdapterJS.class.getClassLoader());
            lastAPSResult = in.readParcelable(DetermineBasalResult.class.getClassLoader());
            lastAPSRun = new Date(in.readLong());
            lastAPSResult = in.readParcelable(APSResult.class.getClassLoader());
        }

        public LastRun() {
        }
    }

    LastRun lastRun = null;

    boolean fragmentEnabled = false;
    boolean fragmentVisible = true;

    public OpenAPSMAFragment() {
        super();
        registerBus();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.openapsma);
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
    public void setFragmentVisible(boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public int getType() {
        return PluginBase.APS;
    }

    @Override
    public APSResult getLastAPSResult() {
        if (lastRun == null) return null;
        return lastRun.lastAPSResult;
    }

    @Override
    public Date getLastAPSRun() {
        return lastRun.lastAPSRun;
    }

    public static OpenAPSMAFragment newInstance() {
        OpenAPSMAFragment fragment = new OpenAPSMAFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openapsma_fragment, container, false);

        run = (Button) view.findViewById(R.id.openapsma_run);
        run.setOnClickListener(this);
        lastRunView = (TextView) view.findViewById(R.id.openapsma_lastrun);
        glucoseStatusView = (TextView) view.findViewById(R.id.openapsma_glucosestatus);
        currentTempView = (TextView) view.findViewById(R.id.openapsma_currenttemp);
        iobDataView = (TextView) view.findViewById(R.id.openapsma_iobdata);
        profileView = (TextView) view.findViewById(R.id.openapsma_profile);
        mealDataView = (TextView) view.findViewById(R.id.openapsma_mealdata);
        resultView = (TextView) view.findViewById(R.id.openapsma_result);
        requestView = (TextView) view.findViewById(R.id.openapsma_request);

//        if (savedInstanceState != null) {
//            lastRun = savedInstanceState.getParcelable("lastrun");
//        }
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
            case R.id.openapsma_run:
                invoke();
                break;
        }

    }

    @Override
    public void invoke() {
        DetermineBasalAdapterJS determineBasalAdapterJS = null;
        try {
            determineBasalAdapterJS = new DetermineBasalAdapterJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return;
        }

        DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpInterface pump = MainApp.getConfigBuilder().getActivePump();

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
            updateResultGUI(getString(R.string.openapsma_nopump));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_nopump));
            return;
        }

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String units = profile.getUnits();

        String maxBgDefault = "180";
        String minBgDefault = "100";
        if (!units.equals(Constants.MGDL)) {
            maxBgDefault = "10";
            minBgDefault = "5";
        }

        Date now = new Date();

        double maxIob = SafeParse.stringToDouble(SP.getString("openapsma_max_iob", "1.5"));
        double maxBasal = SafeParse.stringToDouble(SP.getString("openapsma_max_basal", "1"));
        double minBg = NSProfile.toMgdl(SafeParse.stringToDouble(SP.getString("openapsma_min_bg", minBgDefault)), units);
        double maxBg = NSProfile.toMgdl(SafeParse.stringToDouble(SP.getString("openapsma_max_bg", minBgDefault)), units);
        minBg = Round.roundTo(minBg, 1d);
        maxBg = Round.roundTo(maxBg, 1d);

        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        TreatmentsFragment.MealData mealData = treatments.getMealData();

        maxIob = MainApp.getConfigBuilder().applyMaxIOBConstraints(maxIob);

        determineBasalAdapterJS.setData(profile, maxIob, maxBasal, minBg, maxBg, pump, iobTotal, glucoseStatus, mealData);


        DetermineBasalResult determineBasalResult = determineBasalAdapterJS.invoke();
        determineBasalResult.iob = iobTotal;

        determineBasalAdapterJS.release();

        try {
            determineBasalResult.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        lastRun = new LastRun();
        lastRun.lastDetermineBasalAdapterJS = determineBasalAdapterJS;
        lastRun.lastAPSResult = determineBasalResult;
        lastRun.lastAPSRun = now;
        updateGUI();

        //deviceStatus.suggested = determineBasalResult.json;
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (lastRun != null) {
                        glucoseStatusView.setText(lastRun.lastDetermineBasalAdapterJS.getGlucoseStatusParam());
                        currentTempView.setText(lastRun.lastDetermineBasalAdapterJS.getCurrentTempParam());
                        iobDataView.setText(lastRun.lastDetermineBasalAdapterJS.getIobDataParam());
                        profileView.setText(lastRun.lastDetermineBasalAdapterJS.getProfileParam());
                        mealDataView.setText(lastRun.lastDetermineBasalAdapterJS.getMealDataParam());
                        resultView.setText(lastRun.lastAPSResult.json.toString());
                        requestView.setText(lastRun.lastAPSResult.toString());
                        lastRunView.setText(lastRun.lastAPSRun.toLocaleString());
                    }
                }
            });
    }

    void updateResultGUI(final String text) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultView.setText(text);
                    glucoseStatusView.setText("");
                    currentTempView.setText("");
                    iobDataView.setText("");
                    profileView.setText("");
                    mealDataView.setText("");
                    requestView.setText("");
                    lastRunView.setText("");
                }
            });
    }
}
