package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.PodHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.ErosHistory;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordEntity;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.util.AapsOmnipodUtil;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.pump.common.defs.PumpHistoryEntryGroup;
import info.nightscout.pump.common.defs.TempBasalPair;
import info.nightscout.pump.common.utils.ProfileUtil;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;

public class ErosPodHistoryActivity extends DaggerAppCompatActivity {

    @Inject AAPSLogger aapsLogger;
    @Inject AapsOmnipodUtil aapsOmnipodUtil;
    @Inject ResourceHelper rh;
    @Inject ErosHistory erosHistory;

    private Spinner historyTypeSpinner;
    private TextView statusView;
    private RecyclerView recyclerView;
    private LinearLayoutManager linearLayoutManager;

    private static PumpHistoryEntryGroup selectedGroup = PumpHistoryEntryGroup.All;
    private final List<ErosHistoryRecordEntity> fullHistoryList = new ArrayList<>();
    private final List<ErosHistoryRecordEntity> filteredHistoryList = new ArrayList<>();

    private RecyclerViewAdapter recyclerViewAdapter;
    private boolean manualChange = false;

    private List<TypeList> typeListFull;


    public ErosPodHistoryActivity() {
        super();
    }


    private void prepareData() {
        GregorianCalendar gc = new GregorianCalendar();
        gc.add(Calendar.HOUR_OF_DAY, -24);

        fullHistoryList.addAll(erosHistory.getAllErosHistoryRecordsFromTimestamp(gc.getTimeInMillis()));
    }


    private void filterHistory(PumpHistoryEntryGroup group) {

        this.filteredHistoryList.clear();

        aapsLogger.debug(LTag.PUMP, "Items on full list: {}", fullHistoryList.size());

        if (group == PumpHistoryEntryGroup.All) {
            this.filteredHistoryList.addAll(fullHistoryList);
        } else {
            for (ErosHistoryRecordEntity pumpHistoryEntry : fullHistoryList) {
                if (PodHistoryEntryType.getByCode(pumpHistoryEntry.getPodEntryTypeCode()).getGroup() == group) {
                    this.filteredHistoryList.add(pumpHistoryEntry);
                }
            }
        }

        if (this.recyclerViewAdapter != null) {
            this.recyclerViewAdapter.setHistoryList(this.filteredHistoryList);
            this.recyclerViewAdapter.notifyDataSetChanged();
        }

        aapsLogger.debug(LTag.PUMP, "Items on filtered list: {}", filteredHistoryList.size());
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
        setContentView(R.layout.omnipod_eros_pod_history_activity);

        historyTypeSpinner = findViewById(R.id.omnipod_historytype);
        statusView = findViewById(R.id.omnipod_historystatus);
        recyclerView = findViewById(R.id.omnipod_history_recyclerview);
        recyclerView.setHasFixedSize(true);

        linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        prepareData();

        recyclerViewAdapter = new RecyclerViewAdapter(filteredHistoryList);
        recyclerView.setAdapter(recyclerViewAdapter);

        statusView.setVisibility(View.GONE);

        typeListFull = getTypeList(PumpHistoryEntryGroup.Companion.getTranslatedList(rh));

        ArrayAdapter<TypeList> spinnerAdapter = new ArrayAdapter<>(this, info.nightscout.core.ui.R.layout.spinner_centered, typeListFull);
        historyTypeSpinner.setAdapter(spinnerAdapter);

        historyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (manualChange)
                    return;
                TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
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

    static class TypeList {

        final PumpHistoryEntryGroup entryGroup;
        final String name;

        TypeList(PumpHistoryEntryGroup entryGroup) {
            this.entryGroup = entryGroup;
            this.name = entryGroup.getTranslated();
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<ErosHistoryRecordEntity> historyList;

        RecyclerViewAdapter(List<ErosHistoryRecordEntity> historyList) {
            this.historyList = historyList;
        }


        void setHistoryList(List<ErosHistoryRecordEntity> historyList) {
            this.historyList = historyList;
            Collections.sort(this.historyList);
        }


        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.omnipod_eros_pod_history_item, //
                    viewGroup, false);
            return new HistoryViewHolder(v);
        }


        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            ErosHistoryRecordEntity record = historyList.get(position);

            if (record != null) {
                holder.timeView.setText(record.getDateTimeString());
                holder.typeView.setText(PodHistoryEntryType.getByCode(record.getPodEntryTypeCode()).getResourceId());
                setValue(record, holder.valueView);
            }
        }


        private void setValue(ErosHistoryRecordEntity historyEntry, TextView valueView) {
            //valueView.setText("");

            if (historyEntry.isSuccess()) {
                PodHistoryEntryType entryType = PodHistoryEntryType.getByCode(historyEntry.getPodEntryTypeCode());
                switch (entryType) {

                    case SET_TEMPORARY_BASAL:
                    case SPLIT_TEMPORARY_BASAL: {
                        TempBasalPair tempBasalPair = aapsOmnipodUtil.getGsonInstance().fromJson(historyEntry.getData(), TempBasalPair.class);
                        valueView.setText(rh.gs(R.string.omnipod_eros_history_tbr_value, tempBasalPair.getInsulinRate(), tempBasalPair.getDurationMinutes()));
                    }
                    break;

                    case INSERT_CANNULA:
                    case SET_BASAL_SCHEDULE: {
                        if (historyEntry.getData() != null) {
                            setProfileValue(historyEntry.getData(), valueView);
                        }
                    }
                    break;

                    case SET_BOLUS: {
                        if (historyEntry.getData().contains(";")) {
                            String[] splitVal = historyEntry.getData().split(";");
                            valueView.setText(rh.gs(R.string.omnipod_eros_history_bolus_value_with_carbs, Double.valueOf(splitVal[0]), Double.valueOf(splitVal[1])));
                        } else {
                            valueView.setText(rh.gs(R.string.omnipod_eros_history_bolus_value, Double.valueOf(historyEntry.getData())));
                        }
                    }
                    break;

                    case PLAY_TEST_BEEP: {
                        if (historyEntry.getData() != null) {
                            valueView.setText(historyEntry.getData());
                        }
                    }
                    break;
                    case GET_POD_STATUS:
                    case GET_POD_INFO:
                    case SET_TIME:
                    case INITIALIZE_POD:
                    case CANCEL_TEMPORARY_BASAL_BY_DRIVER:
                    case CANCEL_TEMPORARY_BASAL:
                    case CONFIGURE_ALERTS:
                    case CANCEL_BOLUS:
                    case DEACTIVATE_POD:
                    case DISCARD_POD:
                    case ACKNOWLEDGE_ALERTS:
                    case SUSPEND_DELIVERY:
                    case RESUME_DELIVERY:
                    case UNKNOWN_ENTRY_TYPE:
                    default:
                        valueView.setText("");
                        break;

                }
            } else {
                valueView.setText(historyEntry.getData());
            }

        }

        private void setProfileValue(String data, TextView valueView) {
            aapsLogger.debug(LTag.PUMP, "Profile json:\n" + data);

            try {
                Profile.ProfileValue[] profileValuesArray = aapsOmnipodUtil.getGsonInstance().fromJson(data, Profile.ProfileValue[].class);
                valueView.setText(ProfileUtil.INSTANCE.getBasalProfilesDisplayable(profileValuesArray, PumpType.OMNIPOD_EROS));
            } catch (Exception e) {
                aapsLogger.error(LTag.PUMP, "Problem parsing Profile json. Ex: {}, Data:\n{}", e.getMessage(), data);
                valueView.setText("");
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


        class HistoryViewHolder extends RecyclerView.ViewHolder {

            final TextView timeView;
            final TextView typeView;
            final TextView valueView;

            HistoryViewHolder(View itemView) {
                super(itemView);
                timeView = itemView.findViewById(R.id.omnipod_history_time);
                typeView = itemView.findViewById(R.id.omnipod_history_source);
                valueView = itemView.findViewById(R.id.omnipod_history_description);
            }
        }
    }

}
