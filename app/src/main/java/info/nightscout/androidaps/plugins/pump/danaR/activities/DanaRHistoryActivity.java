package info.nightscout.androidaps.plugins.pump.danaR.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashActivity;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.ToastUtils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class DanaRHistoryActivity extends NoSplashActivity {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);
    private CompositeDisposable disposable = new CompositeDisposable();

    static Profile profile = null;

    Spinner historyTypeSpinner;
    TextView statusView;
    Button reloadButton;
    RecyclerView recyclerView;
    LinearLayoutManager llm;

    static byte showingType = RecordTypes.RECORD_TYPE_ALARM;
    List<DanaRHistoryRecord> historyList = new ArrayList<>();

    public static class TypeList {
        public byte type;
        String name;

        TypeList(byte type, String name) {
            this.type = type;
            this.name = name;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    public DanaRHistoryActivity() {
        super();
    }


    @Override
    protected void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPumpStatusChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> statusView.setText(event.getStatus()), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventDanaRSyncStatus.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    if (L.isEnabled(L.PUMP))
                        log.debug("EventDanaRSyncStatus: " + event.getMessage());
                    statusView.setText(event.getMessage());
                }, FabricPrivacy::logException)
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        disposable.clear();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danar_historyactivity);

        historyTypeSpinner = findViewById(R.id.danar_historytype);
        statusView = findViewById(R.id.danar_historystatus);
        reloadButton = findViewById(R.id.danar_historyreload);
        recyclerView = findViewById(R.id.danar_history_recyclerview);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(historyList);
        recyclerView.setAdapter(adapter);

        statusView.setVisibility(View.GONE);

        boolean isKorean = DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PUMP);
        boolean isRS = DanaRSPlugin.getPlugin().isEnabled(PluginType.PUMP);

        // Types

        ArrayList<TypeList> typeList = new ArrayList<>();
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_ALARM, MainApp.gs(R.string.danar_history_alarm)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, MainApp.gs(R.string.danar_history_basalhours)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_BOLUS, MainApp.gs(R.string.danar_history_bolus)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_CARBO, MainApp.gs(R.string.danar_history_carbohydrates)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_DAILY, MainApp.gs(R.string.danar_history_dailyinsulin)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_GLUCOSE, MainApp.gs(R.string.danar_history_glucose)));
        if (!isKorean && !isRS) {
            typeList.add(new TypeList(RecordTypes.RECORD_TYPE_ERROR, MainApp.gs(R.string.danar_history_errors)));
        }
        if (isRS)
            typeList.add(new TypeList(RecordTypes.RECORD_TYPE_PRIME, MainApp.gs(R.string.danar_history_prime)));
        if (!isKorean) {
            typeList.add(new TypeList(RecordTypes.RECORD_TYPE_REFILL, MainApp.gs(R.string.danar_history_refill)));
            typeList.add(new TypeList(RecordTypes.RECORD_TYPE_SUSPEND, MainApp.gs(R.string.danar_history_syspend)));
        }
        ArrayAdapter<TypeList> spinnerAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_centered, typeList);
        historyTypeSpinner.setAdapter(spinnerAdapter);

        reloadButton.setOnClickListener(v -> {
            final TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
            runOnUiThread(() -> {
                reloadButton.setVisibility(View.GONE);
                statusView.setVisibility(View.VISIBLE);
            });
            clearCardView();
            ConfigBuilderPlugin.getPlugin().getCommandQueue().loadHistory(selected.type, new Callback() {
                @Override
                public void run() {
                    loadDataFromDB(selected.type);
                    runOnUiThread(() -> {
                        reloadButton.setVisibility(View.VISIBLE);
                        statusView.setVisibility(View.GONE);
                    });
                }
            });
        });

        historyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
                loadDataFromDB(selected.type);
                showingType = selected.type;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                clearCardView();
            }
        });
        profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.noprofile));
            finish();
        }
    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<DanaRHistoryRecord> historyList;

        RecyclerViewAdapter(List<DanaRHistoryRecord> historyList) {
            this.historyList = historyList;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.danar_history_item, viewGroup, false);
            return new HistoryViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            DanaRHistoryRecord record = historyList.get(position);
            holder.time.setText(DateUtil.dateAndTimeString(record.recordDate));
            holder.value.setText(DecimalFormatter.to2Decimal(record.recordValue));
            holder.stringvalue.setText(record.stringRecordValue);
            holder.bolustype.setText(record.bolusType);
            holder.duration.setText(DecimalFormatter.to0Decimal(record.recordDuration));
            holder.alarm.setText(record.recordAlarm);
            switch (showingType) {
                case RecordTypes.RECORD_TYPE_ALARM:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.VISIBLE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.VISIBLE);
                    break;
                case RecordTypes.RECORD_TYPE_BOLUS:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.VISIBLE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.VISIBLE);
                    holder.duration.setVisibility(View.VISIBLE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
                case RecordTypes.RECORD_TYPE_DAILY:
                    holder.dailybasal.setText(DecimalFormatter.to2Decimal(record.recordDailyBasal) + "U");
                    holder.dailybolus.setText(DecimalFormatter.to2Decimal(record.recordDailyBolus) + "U");
                    holder.dailytotal.setText(DecimalFormatter.to2Decimal(record.recordDailyBolus + record.recordDailyBasal) + "U");
                    holder.time.setText(DateUtil.dateString(record.recordDate));
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.GONE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.VISIBLE);
                    holder.dailybolus.setVisibility(View.VISIBLE);
                    holder.dailytotal.setVisibility(View.VISIBLE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
                case RecordTypes.RECORD_TYPE_GLUCOSE:
                    holder.value.setText(Profile.toUnitsString(record.recordValue, record.recordValue * Constants.MGDL_TO_MMOLL, profile.getUnits()));
                    // rest is the same
                case RecordTypes.RECORD_TYPE_CARBO:
                case RecordTypes.RECORD_TYPE_BASALHOUR:
                case RecordTypes.RECORD_TYPE_ERROR:
                case RecordTypes.RECORD_TYPE_PRIME:
                case RecordTypes.RECORD_TYPE_REFILL:
                case RecordTypes.RECORD_TYPE_TB:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.VISIBLE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
                case RecordTypes.RECORD_TYPE_SUSPEND:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.GONE);
                    holder.stringvalue.setVisibility(View.VISIBLE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView time;
            TextView value;
            TextView bolustype;
            TextView stringvalue;
            TextView duration;
            TextView dailybasal;
            TextView dailybolus;
            TextView dailytotal;
            TextView alarm;

            HistoryViewHolder(View itemView) {
                super(itemView);
                cv = itemView.findViewById(R.id.danar_history_cardview);
                time = itemView.findViewById(R.id.danar_history_time);
                value = itemView.findViewById(R.id.danar_history_value);
                bolustype = itemView.findViewById(R.id.danar_history_bolustype);
                stringvalue = itemView.findViewById(R.id.danar_history_stringvalue);
                duration = itemView.findViewById(R.id.danar_history_duration);
                dailybasal = itemView.findViewById(R.id.danar_history_dailybasal);
                dailybolus = itemView.findViewById(R.id.danar_history_dailybolus);
                dailytotal = itemView.findViewById(R.id.danar_history_dailytotal);
                alarm = itemView.findViewById(R.id.danar_history_alarm);
            }
        }
    }

    private void loadDataFromDB(byte type) {
        historyList = MainApp.getDbHelper().getDanaRHistoryRecordsByType(type);

        runOnUiThread(() -> recyclerView.swapAdapter(new RecyclerViewAdapter(historyList), false));
    }

    private void clearCardView() {
        historyList = new ArrayList<>();
        runOnUiThread(() -> recyclerView.swapAdapter(new RecyclerViewAdapter(historyList), false));
    }
}
