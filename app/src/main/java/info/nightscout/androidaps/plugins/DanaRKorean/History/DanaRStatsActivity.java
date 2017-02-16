package info.nightscout.androidaps.plugins.DanaRKorean.History;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.CircadianPercentageProfile.CircadianPercentageProfilePlugin;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.DanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRSyncStatus;
import info.nightscout.androidaps.plugins.DanaRKorean.Services.ExecutionService;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class DanaRStatsActivity extends Activity {
    private static Logger log = LoggerFactory.getLogger(DanaRStatsActivity.class);

    private boolean mBounded;
    private static ExecutionService mExecutionService;

    private Handler mHandler;
    private static HandlerThread mHandlerThread;

    TextView statusView, statsMessage,totalBaseBasal2;
    EditText totalBaseBasal;
    Button reloadButton;
    LinearLayoutManager llm;
    TableLayout tl,ctl,etl;
    String TBB;
    double magicNumber;
    DecimalFormat decimalFormat;

    List<DanaRHistoryRecord> historyList = new ArrayList<>();

    public DanaRStatsActivity() {
        super();
        mHandlerThread = new HandlerThread(DanaRStatsActivity.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ExecutionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mBounded) {
            unbindService(mConnection);
            mBounded = false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View myView = getCurrentFocus();
            if ( myView instanceof EditText) {
                Rect rect = new Rect();
                myView.getGlobalVisibleRect(rect);
                if (!rect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    myView.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent( event );
    }

    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            mBounded = false;
            mExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            mBounded = true;
            ExecutionService.LocalBinder mLocalBinder = (ExecutionService.LocalBinder) service;
            mExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danar_statsactivity);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        statusView = (TextView) findViewById(R.id.danar_stats_connection_status);
        reloadButton = (Button) findViewById(R.id.danar_statsreload);
        totalBaseBasal = (EditText) findViewById(R.id.danar_stats_editTotalBaseBasal);
        totalBaseBasal2 = (TextView) findViewById(R.id.danar_stats_editTotalBaseBasal2);
        statsMessage = (TextView) findViewById(R.id.danar_stats_Message);

        statusView.setVisibility(View.GONE);
        statsMessage.setVisibility(View.GONE);

        totalBaseBasal2.setEnabled(false);
        totalBaseBasal2.setClickable(false);
        totalBaseBasal2.setFocusable(false);
        totalBaseBasal2.setInputType(0);

        decimalFormat = new DecimalFormat("0.000");
        llm = new LinearLayoutManager(this);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        TBB = preferences.getString("TBB", "10.00");
        totalBaseBasal.setText(TBB);

        ProfileInterface pi = ConfigBuilderPlugin.getActiveProfile();
        if (pi != null && pi instanceof CircadianPercentageProfilePlugin){
            double cppTBB = ((CircadianPercentageProfilePlugin)pi).baseBasalSum();
            totalBaseBasal.setText(decimalFormat.format(cppTBB));
            SharedPreferences.Editor edit = preferences.edit();
            edit.putString("TBB",totalBaseBasal.getText().toString());
            edit.commit();
            TBB = preferences.getString("TBB", "");
        }

        // stats table
        tl = (TableLayout) findViewById(R.id.main_table);
        TableRow tr_head = new TableRow(this);
        tr_head.setBackgroundColor(Color.DKGRAY);
        tr_head.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        TextView label_date = new TextView(this);
        label_date.setText(getString(R.string.danar_stats_date));
        label_date.setTextColor(Color.WHITE);
        tr_head.addView(label_date);

        TextView label_basalrate = new TextView(this);
        label_basalrate.setText(getString(R.string.danar_stats_basalrate));
        label_basalrate.setTextColor(Color.WHITE);
        tr_head.addView(label_basalrate);

        TextView label_bolus = new TextView(this);
        label_bolus.setText(getString(R.string.danar_stats_bolus));
        label_bolus.setTextColor(Color.WHITE);
        tr_head.addView(label_bolus);

        TextView label_tdd = new TextView(this);
        label_tdd.setText(getString(R.string.danar_stats_tdd));
        label_tdd.setTextColor(Color.WHITE);
        tr_head.addView(label_tdd);

        TextView label_ratio = new TextView(this);
        label_ratio.setText(getString(R.string.danar_stats_ratio));
        label_ratio.setTextColor(Color.WHITE);
        tr_head.addView(label_ratio);

        // add stats headers to tables
        tl.addView(tr_head, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        // cumulative table
        ctl = (TableLayout) findViewById(R.id.cumulative_table);
        TableRow ctr_head = new TableRow(this);
        ctr_head.setBackgroundColor(Color.DKGRAY);
        ctr_head.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        TextView label_cum_amount_days = new TextView(this);
        label_cum_amount_days.setText(getString(R.string.danar_stats_amount_days));
        label_cum_amount_days.setTextColor(Color.WHITE);
        ctr_head.addView(label_cum_amount_days);

        TextView label_cum_tdd = new TextView(this);
        label_cum_tdd.setText(getString(R.string.danar_stats_tdd));
        label_cum_tdd.setTextColor(Color.WHITE);
        ctr_head.addView(label_cum_tdd);

        TextView label_cum_ratio = new TextView(this);
        label_cum_ratio.setText(getString(R.string.danar_stats_ratio));
        label_cum_ratio.setTextColor(Color.WHITE);
        ctr_head.addView(label_cum_ratio);

        // add cummulative headers to tables
        ctl.addView(ctr_head, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        // expontial table
        etl = (TableLayout) findViewById(R.id.expweight_table);
        TableRow etr_head = new TableRow(this);
        etr_head.setBackgroundColor(Color.DKGRAY);
        etr_head.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        TextView label_exp_weight = new TextView(this);
        label_exp_weight.setText(getString(R.string.danar_stats_weight));
        label_exp_weight.setTextColor(Color.WHITE);
        etr_head.addView(label_exp_weight);

        TextView label_exp_tdd = new TextView(this);
        label_exp_tdd.setText(getString(R.string.danar_stats_tdd));
        label_exp_tdd.setTextColor(Color.WHITE);
        etr_head.addView(label_exp_tdd);

        TextView label_exp_ratio = new TextView(this);
        label_exp_ratio.setText(getString(R.string.danar_stats_ratio));
        label_exp_ratio.setTextColor(Color.WHITE);
        etr_head.addView(label_exp_ratio);

        // add expontial headers to tables
        etl.addView(etr_head, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExecutionService.isConnected() || mExecutionService.isConnecting()) {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.pumpbusy));
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.GONE);
                                statusView.setVisibility(View.VISIBLE);
                                statsMessage.setVisibility(View.VISIBLE);
                                statsMessage.setText(getString(R.string.danar_stats_warning_Message));
                            }
                        });
                        mExecutionService.loadHistory(RecordTypes.RECORD_TYPE_DAILY);
                        loadDataFromDB(RecordTypes.RECORD_TYPE_DAILY);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.VISIBLE);
                                statusView.setVisibility(View.GONE);
                                statsMessage.setVisibility(View.GONE);
                            }
                        });
                    }
                });
            }
        });

        totalBaseBasal.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId== EditorInfo.IME_ACTION_DONE){
                    totalBaseBasal.clearFocus();
                    return true;
                }
                return false;
            }
        });

        totalBaseBasal.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus){
                    totalBaseBasal.getText().clear();
                } else {
                    SharedPreferences.Editor edit = preferences.edit();
                    edit.putString("TBB",totalBaseBasal.getText().toString());
                    edit.commit();
                    TBB = preferences.getString("TBB", "");
                    loadDataFromDB(RecordTypes.RECORD_TYPE_DAILY);
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(totalBaseBasal.getWindowToken(), 0);
                }
            }
        });

        loadDataFromDB(RecordTypes.RECORD_TYPE_DAILY);
    }

    private void loadDataFromDB(byte type) {
        try {
            Dao<DanaRHistoryRecord, String> dao = MainApp.getDbHelper().getDaoDanaRHistory();
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("recordDate", false);
            Where where = queryBuilder.where();
            where.eq("recordCode", type);
            queryBuilder.limit(10L);
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            historyList = dao.query(preparedQuery);
        } catch (SQLException e) {
            e.printStackTrace();
            historyList = new ArrayList<>();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cleanTable(tl);
                cleanTable(ctl);
                cleanTable(etl);
                DateFormat df = new SimpleDateFormat("dd.MM.");

                if(TextUtils.isEmpty(TBB)) {
                    totalBaseBasal.setError("Please Enter Total Base Basal");
                    return;
                }
                else {
                    magicNumber = SafeParse.stringToDouble(TBB);
                }

                magicNumber *=2;
                totalBaseBasal2.setText(decimalFormat.format(magicNumber));

                int i = 0;
                double sum = 0d;
                double weighted03 = 0d;
                double weighted05 = 0d;
                double weighted07 = 0d;

                for (DanaRHistoryRecord record: historyList) {
                    double tdd= record.getRecordDailyBolus() + record.getRecordDailyBasal();

                    // Create the table row
                    TableRow tr = new TableRow(DanaRStatsActivity.this);
                    if(i%2!=0) tr.setBackgroundColor(Color.DKGRAY);
                    tr.setId(100+i);
                    tr.setLayoutParams(new TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT));

                    // Here create the TextView dynamically
                    TextView labelDATE = new TextView(DanaRStatsActivity.this);
                    labelDATE.setId(200+i);
                    labelDATE.setText(df.format(new Date(record.getRecordDate())));
                    labelDATE.setTextColor(Color.WHITE);
                    tr.addView(labelDATE);

                    TextView labelBASAL = new TextView(DanaRStatsActivity.this);
                    labelBASAL.setId(300+i);
                    labelBASAL.setText(DecimalFormatter.to2Decimal(record.getRecordDailyBasal()) + " U");
                    labelBASAL.setTextColor(Color.WHITE);
                    tr.addView(labelBASAL);

                    TextView labelBOLUS = new TextView(DanaRStatsActivity.this);
                    labelBOLUS.setId(400+i);
                    labelBOLUS.setText(DecimalFormatter.to2Decimal(record.getRecordDailyBolus()) + " U");
                    labelBOLUS.setTextColor(Color.WHITE);
                    tr.addView(labelBOLUS);

                    TextView labelTDD = new TextView(DanaRStatsActivity.this);
                    labelTDD.setId(500+i);
                    labelTDD.setText(DecimalFormatter.to2Decimal(tdd) + " U");
                    labelTDD.setTextColor(Color.WHITE);
                    tr.addView(labelTDD);

                    TextView labelRATIO = new TextView(DanaRStatsActivity.this);
                    labelRATIO.setId(600+i);
                    labelRATIO.setText(Math.round(100*tdd/magicNumber) +" %");
                    labelRATIO.setTextColor(Color.WHITE);
                    tr.addView(labelRATIO);

                    // add stats rows to tables
                    tl.addView(tr, new TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT));

                    sum = sum + tdd;
                    i++;

                    // Create the cumtable row
                    TableRow ctr = new TableRow(DanaRStatsActivity.this);
                    if(i%2==0) ctr.setBackgroundColor(Color.DKGRAY);
                    ctr.setId(700+i);
                    ctr.setLayoutParams(new TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT));

                    // Here create the TextView dynamically
                    TextView labelDAYS = new TextView(DanaRStatsActivity.this);
                    labelDAYS.setId(800+i);
                    labelDAYS.setText("" + i);
                    labelDAYS.setTextColor(Color.WHITE);
                    ctr.addView(labelDAYS);

                    TextView labelCUMTDD = new TextView(DanaRStatsActivity.this);
                    labelCUMTDD.setId(900+i);
                    labelCUMTDD.setText(DecimalFormatter.to2Decimal(sum/i) + " U");
                    labelCUMTDD.setTextColor(Color.WHITE);
                    ctr.addView(labelCUMTDD);

                    TextView labelCUMRATIO = new TextView(DanaRStatsActivity.this);
                    labelCUMRATIO.setId(1000+i);
                    labelCUMRATIO.setText(Math.round(100*sum/i/magicNumber) + " %");
                    labelCUMRATIO.setTextColor(Color.WHITE);
                    ctr.addView(labelCUMRATIO);

                    // add cummulative rows to tables
                    ctl.addView(ctr, new TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT));
                }

                if (historyList.size()<3 || !(df.format(new Date(historyList.get(0).getRecordDate())).equals(df.format(new Date(System.currentTimeMillis() - 1000*60*60*24))))){
                    statsMessage.setVisibility(View.VISIBLE);
                    statsMessage.setText(getString(R.string.danar_stats_olddata_Message));

                } else {
                    tl.setBackgroundColor(Color.TRANSPARENT);
                }

                Collections.reverse(historyList);

                i = 0;

                for (DanaRHistoryRecord record: historyList) {
                    double tdd= record.getRecordDailyBolus() + record.getRecordDailyBasal();
                    if(i == 0 ) {
                        weighted03 = tdd;
                        weighted05 = tdd;
                        weighted07 = tdd;

                    } else {
                        weighted07 = (weighted07*0.3 + tdd*0.7);
                        weighted05 = (weighted05*0.5 + tdd*0.5);
                        weighted03 = (weighted03*0.7 + tdd*0.3);
                    }
                    i++;
                }

                // Create the exptable row
                TableRow etr = new TableRow(DanaRStatsActivity.this);
                if(i%2!=0) etr.setBackgroundColor(Color.DKGRAY);
                etr.setId(1100+i);
                etr.setLayoutParams(new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));

                // Here create the TextView dynamically
                TextView labelWEIGHT = new TextView(DanaRStatsActivity.this);
                labelWEIGHT.setId(1200+i);
                labelWEIGHT.setText("0.3\n" + "0.5\n" + "0.7");
                labelWEIGHT.setTextColor(Color.WHITE);
                etr.addView(labelWEIGHT);

                TextView labelEXPTDD = new TextView(DanaRStatsActivity.this);
                labelEXPTDD.setId(1300+i);
                labelEXPTDD.setText(DecimalFormatter.to2Decimal(weighted03)
                        + " U\n" + DecimalFormatter.to2Decimal(weighted05)
                        + " U\n" + DecimalFormatter.to2Decimal(weighted07) + " U");
                labelEXPTDD.setTextColor(Color.WHITE);
                etr.addView(labelEXPTDD);

                TextView labelEXPRATIO = new TextView(DanaRStatsActivity.this);
                labelEXPRATIO.setId(1400+i);
                labelEXPRATIO.setText(Math.round(100*weighted03/magicNumber) +" %\n"
                        + Math.round(100*weighted05/magicNumber) +" %\n"
                        + Math.round(100*weighted07/magicNumber) +" %");
                labelEXPRATIO.setTextColor(Color.WHITE);
                etr.addView(labelEXPRATIO);

                // add exponentail rows to tables
                etl.addView(etr, new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));
            }
        });
    }

    private void cleanTable(TableLayout table) {
        int childCount = table.getChildCount();
        // Remove all rows except the first one
        if (childCount > 1) {
            table.removeViews(1, childCount - 1);
        }
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRSyncStatus s) {
        log.debug("EventDanaRSyncStatus: " + s.message);
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        statusView.setText(s.message);
                    }
                });
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRConnectionStatus c) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (c.sStatus == EventDanaRConnectionStatus.CONNECTING) {
                            statusView.setText(String.format(getString(R.string.danar_history_connectingfor), c.sSecondsElapsed));
                            log.debug("EventDanaRConnectionStatus: " + "Connecting for " + c.sSecondsElapsed + "s");
                        } else if (c.sStatus == EventDanaRConnectionStatus.CONNECTED) {
                            statusView.setText(MainApp.sResources.getString(R.string.connected));
                            log.debug("EventDanaRConnectionStatus: Connected");
                        } else {
                            statusView.setText(MainApp.sResources.getString(R.string.disconnected));
                            log.debug("EventDanaRConnectionStatus: Disconnected");
                        }
                    }
                }
        );
    }
}