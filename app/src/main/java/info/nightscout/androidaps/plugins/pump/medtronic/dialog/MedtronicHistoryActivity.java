package info.nightscout.androidaps.plugins.pump.medtronic.dialog;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashActivity;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryGroup;

public class MedtronicHistoryActivity extends NoSplashActivity {

    private static Logger LOG = LoggerFactory.getLogger(L.PUMP);

    Spinner historyTypeSpinner;
    TextView statusView;
    RecyclerView recyclerView;
    LinearLayoutManager llm;

    static TypeList showingType = null;
    static PumpHistoryEntryGroup selectedGroup = PumpHistoryEntryGroup.All;
    List<PumpHistoryEntry> filteredHistoryList = new ArrayList<>();

    RecyclerViewAdapter recyclerViewAdapter;
    boolean manualChange = false;

    List<TypeList> typeListFull;


    public MedtronicHistoryActivity() {
        super();
    }


    private void filterHistory(PumpHistoryEntryGroup group) {

        this.filteredHistoryList.clear();

        List<PumpHistoryEntry> list = new ArrayList<>();
        list.addAll(MedtronicPumpPlugin.getPlugin().getMedtronicHistoryData().getAllHistory());

        //LOG.debug("Items on full list: {}", list.size());

        if (group == PumpHistoryEntryGroup.All) {
            this.filteredHistoryList.addAll(list);
        } else {
            for (PumpHistoryEntry pumpHistoryEntry : list) {
                if (pumpHistoryEntry.getEntryType().getGroup() == group) {
                    this.filteredHistoryList.add(pumpHistoryEntry);
                }
            }
        }

        if (this.recyclerViewAdapter != null) {
            this.recyclerViewAdapter.setHistoryList(this.filteredHistoryList);
            this.recyclerViewAdapter.notifyDataSetChanged();
        }

        //LOG.debug("Items on filtered list: {}", filteredHistoryList.size());
    }


    @Override
    protected void onResume() {
        super.onResume();
        filterHistory(selectedGroup);
        setHistoryTypeSpinner();
    }


    private void setHistoryTypeSpinner() {
        this.manualChange = true;

        for (int i = 0; i < typeListFull.size(); i++) {
            if (typeListFull.get(i).entryGroup == selectedGroup) {
                historyTypeSpinner.setSelection(i);
                break;
            }
        }

        SystemClock.sleep(200);
        this.manualChange = false;
    }


    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medtronic_history_activity);

        historyTypeSpinner = (Spinner) findViewById(R.id.medtronic_historytype);
        statusView = (TextView) findViewById(R.id.medtronic_historystatus);
        recyclerView = (RecyclerView) findViewById(R.id.medtronic_history_recyclerview);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);

        recyclerViewAdapter = new RecyclerViewAdapter(filteredHistoryList);
        recyclerView.setAdapter(recyclerViewAdapter);

        statusView.setVisibility(View.GONE);

        typeListFull = getTypeList(PumpHistoryEntryGroup.getList());

        ArrayAdapter<TypeList> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_centered, typeListFull);
        historyTypeSpinner.setAdapter(spinnerAdapter);

        historyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (manualChange)
                    return;
                TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
                showingType = selected;
                selectedGroup = selected.entryGroup;
                filterHistory(selectedGroup);
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (manualChange)
                    return;
                filterHistory(PumpHistoryEntryGroup.All);
            }
        });

    }


    private List<TypeList> getTypeList(List<PumpHistoryEntryGroup> list) {

        ArrayList<TypeList> typeList = new ArrayList<>();

        for (PumpHistoryEntryGroup pumpHistoryEntryGroup : list) {
            typeList.add(new TypeList(pumpHistoryEntryGroup));
        }

        return typeList;
    }

    public static class TypeList {

        PumpHistoryEntryGroup entryGroup;
        String name;


        TypeList(PumpHistoryEntryGroup entryGroup) {
            this.entryGroup = entryGroup;
            this.name = entryGroup.getTranslated();
        }


        @Override
        public String toString() {
            return name;
        }
    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<PumpHistoryEntry> historyList;


        RecyclerViewAdapter(List<PumpHistoryEntry> historyList) {
            this.historyList = historyList;
        }


        public void setHistoryList(List<PumpHistoryEntry> historyList) {
            // this.historyList.clear();
            // this.historyList.addAll(historyList);

            this.historyList = historyList;

            // this.notifyDataSetChanged();
        }


        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.medtronic_history_item, //
                    viewGroup, false);
            return new HistoryViewHolder(v);
        }


        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            PumpHistoryEntry record = historyList.get(position);

            if (record != null) {
                holder.timeView.setText(record.getDateTimeString());
                holder.typeView.setText(record.getEntryType().getDescription());
                holder.valueView.setText(record.getDisplayableValue());
            }
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

            TextView timeView;
            TextView typeView;
            TextView valueView;


            HistoryViewHolder(View itemView) {
                super(itemView);
                // cv = (CardView)itemView.findViewById(R.id.rileylink_history_item);
                timeView = (TextView) itemView.findViewById(R.id.medtronic_history_time);
                typeView = (TextView) itemView.findViewById(R.id.medtronic_history_source);
                valueView = (TextView) itemView.findViewById(R.id.medtronic_history_description);
            }
        }
    }

}
