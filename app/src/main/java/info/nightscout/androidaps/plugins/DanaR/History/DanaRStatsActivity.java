package info.nightscout.androidaps.plugins.DanaR.History;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.view.View;
import android.widget.Button;
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
import info.nightscout.androidaps.plugins.DanaR.Services.ExecutionService;
import info.nightscout.androidaps.plugins.DanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRSyncStatus;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.ToastUtils;

public class DanaRStatsActivity extends Activity {
    private static Logger log = LoggerFactory.getLogger(DanaRStatsActivity.class);

    private boolean mBounded;
    private static ExecutionService mExecutionService;

    private Handler mHandler;
    private static HandlerThread mHandlerThread;

    TextView statusView;
    TextView mainView;
    Button reloadButton;
    LinearLayoutManager llm;

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

        statusView = (TextView) findViewById(R.id.danar_historystatus);
        mainView = (TextView) findViewById(R.id.danar_stats_textview);
        reloadButton = (Button) findViewById(R.id.danar_historyreload);
        llm = new LinearLayoutManager(this);
        statusView.setVisibility(View.GONE);


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
                            }
                        });
                        mExecutionService.loadHistory(RecordTypes.RECORD_TYPE_DAILY);
                        loadDataFromDB(RecordTypes.RECORD_TYPE_DAILY);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.VISIBLE);
                                statusView.setVisibility(View.GONE);
                            }
                        });
                    }
                });
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
                mainView.setText("");
                DateFormat df = new SimpleDateFormat("dd.MM. - ");

                double magicNumber = 18d;

                ProfileInterface pi = ConfigBuilderPlugin.getActiveProfile();
                if (pi instanceof CircadianPercentageProfilePlugin){
                    magicNumber = ((CircadianPercentageProfilePlugin)pi).baseBasalSum();
                }

                magicNumber *=2;

                int i = 0;
                double sum = 0d;
                double weighted03 = 0d;
                double weighted05 = 0d;
                double weighted07 = 0d;
                String avg_string = "\n" + getString(R.string.danar_stats_avg);
                String weighted_string = "\n\n" + getString(R.string.danar_stats_expweight);

                for (DanaRHistoryRecord record: historyList) {
                    double tdd= record.getRecordDailyBolus() + record.getRecordDailyBasal();
                    mainView.append(df.format(new Date(record.getRecordDate())));
                    mainView.append(getString(R.string.danar_stats_basalrate) + DecimalFormatter.to2Decimal(record.getRecordDailyBasal()));
                    mainView.append(getString(R.string.danar_stats_bolus) + DecimalFormatter.to2Decimal(record.getRecordDailyBolus()));
                    mainView.append(getString(R.string.danar_stats_tdi) + DecimalFormatter.to2Decimal(tdd));
                    mainView.append(" " + Math.round(100*tdd/magicNumber) +"%");
                    mainView.append("\n");
                    sum = sum + tdd;
                    i++;
                    avg_string = avg_string + "\n " + i +": " + DecimalFormatter.to2Decimal(sum/i) + " " + Math.round(100*sum/i/magicNumber) +"%";
                }

                if (historyList.size()<3 || !(df.format(new Date(historyList.get(0).getRecordDate())).equals(df.format(new Date(System.currentTimeMillis() - 1000*60*60*24))))){
                    mainView.setBackgroundColor(Color.RED);
                } else {
                    mainView.setBackgroundColor(Color.TRANSPARENT);
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
                weighted_string = weighted_string + "\n 0.3 " + DecimalFormatter.to2Decimal(weighted03) + " " + Math.round(100*weighted03/magicNumber) +"%";
                weighted_string = weighted_string + "\n 0.5 " + DecimalFormatter.to2Decimal(weighted05) + " " + Math.round(100*weighted05/magicNumber) +"%";
                weighted_string = weighted_string + "\n 0.7 " + DecimalFormatter.to2Decimal(weighted07) + " " + Math.round(100*weighted07/magicNumber) +"%";


                mainView.append(avg_string);
                mainView.append(weighted_string);
            }
        });
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