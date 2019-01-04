package info.nightscout.androidaps.plugins.PumpMedtronic.dialog;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntryGroup;

public class MedtronicHistoryActivity extends Activity {

    private static Logger LOG = LoggerFactory.getLogger(L.PUMP);

    private Handler mHandler;

    static Profile profile = null;

    Spinner historyTypeSpinner;
    TextView statusView;
    // Button reloadButton;
    // Button syncButton;
    RecyclerView recyclerView;
    LinearLayoutManager llm;

    static PumpHistoryEntryGroup showingType = PumpHistoryEntryGroup.All;
    // List<PumpHistoryEntry> fullHistoryList = null;
    List<PumpHistoryEntry> filteredHistoryList = new ArrayList<>();


    // public static class TypeList {
    //
    // public byte type;
    // String name;
    //
    //
    // TypeList(byte type, String name) {
    // this.type = type;
    // this.name = name;
    // }
    //
    //
    // @Override
    // public String toString() {
    // return name;
    // }
    // }

    public MedtronicHistoryActivity() {
        super();
        HandlerThread mHandlerThread = new HandlerThread(MedtronicHistoryActivity.class.getSimpleName());
        mHandlerThread.start();
        // this.fullHistoryList = MedtronicPumpPlugin.getPlugin().getMedtronicHistoryData().getAllHistory();
        filterHistory(this.showingType);
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }


    private void filterHistory(PumpHistoryEntryGroup group) {

        this.filteredHistoryList.clear();

        List<PumpHistoryEntry> list = new ArrayList<>();
        list.addAll(MedtronicPumpPlugin.getPlugin().getMedtronicHistoryData().getAllHistory());

        LOG.debug("Items on full list: {}", list.size());

        if (group == PumpHistoryEntryGroup.All) {
            this.filteredHistoryList.addAll(list);
        } else {
            for (PumpHistoryEntry pumpHistoryEntry : list) {
                if (pumpHistoryEntry.getEntryType().getGroup() == group) {
                    this.filteredHistoryList.add(pumpHistoryEntry);
                }
            }
        }

        LOG.debug("Items on filtered list: {}", filteredHistoryList.size());
    }


    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        filterHistory(showingType);
    }


    @Override
    protected void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medtronic_history_activity);

        historyTypeSpinner = (Spinner)findViewById(R.id.medtronic_historytype);
        statusView = (TextView)findViewById(R.id.medtronic_historystatus);
        // reloadButton = (Button)findViewById(R.id.medtronic_historyreload);
        // syncButton = (Button)findViewById(R.id.medtronic_historysync);
        recyclerView = (RecyclerView)findViewById(R.id.medtronic_history_recyclerview);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(filteredHistoryList);
        recyclerView.setAdapter(adapter);

        statusView.setVisibility(View.GONE);

        boolean isKorean = DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PUMP);
        boolean isRS = DanaRSPlugin.getPlugin().isEnabled(PluginType.PUMP);

        // Types

        // ArrayList<TypeList> typeList = new ArrayList<>();
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_ALARM, MainApp.gs(R.string.danar_history_alarm)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, MainApp.gs(R.string.danar_history_basalhours)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_BOLUS, MainApp.gs(R.string.danar_history_bolus)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_CARBO, MainApp.gs(R.string.danar_history_carbohydrates)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_DAILY, MainApp.gs(R.string.danar_history_dailyinsulin)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_GLUCOSE, MainApp.gs(R.string.danar_history_glucose)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_ERROR, MainApp.gs(R.string.danar_history_errors)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_PRIME, MainApp.gs(R.string.danar_history_prime)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_REFILL, MainApp.gs(R.string.danar_history_refill)));
        // typeList.add(new TypeList(RecordTypes.RECORD_TYPE_SUSPEND, MainApp.gs(R.string.danar_history_syspend)));

        ArrayAdapter<PumpHistoryEntryGroup> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_centered,
            PumpHistoryEntryGroup.getList());
        historyTypeSpinner.setAdapter(spinnerAdapter);

        historyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PumpHistoryEntryGroup selected = (PumpHistoryEntryGroup)historyTypeSpinner.getSelectedItem();
                showingType = selected;
                filterHistory(selected);
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                filterHistory(showingType);
            }
        });

    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<PumpHistoryEntry> historyList;


        RecyclerViewAdapter(List<PumpHistoryEntry> historyList) {
            this.historyList = historyList;
        }


        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.rileylink_status_history_item, //
                viewGroup, false);
            return new HistoryViewHolder(v);
        }


        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            PumpHistoryEntry record = historyList.get(position);

            holder.timeView.setText(record.getDateTimeString());
            holder.typeView.setText(record.getEntryType().getDescription());
            holder.valueView.setText(record.getDisplayableValue());
        }


        @Override
        public int getItemCount() {
            return historyList.size();
        }


        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {

            CardView cv;
            TextView timeView;
            TextView typeView;
            TextView valueView;


            HistoryViewHolder(View itemView) {
                super(itemView);
                // cv = (CardView)itemView.findViewById(R.id.rileylink_history_item);
                timeView = (TextView)itemView.findViewById(R.id.rileylink_history_time);
                typeView = (TextView)itemView.findViewById(R.id.rileylink_history_source);
                valueView = (TextView)itemView.findViewById(R.id.rileylink_history_description);
            }
        }
    }

}
