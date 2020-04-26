package info.nightscout.androidaps.plugins.treatments.fragments;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.Translator;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Created by mike on 13/01/17.
 */

public class TreatmentsCareportalFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    private RecyclerView recyclerView;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.CareportalEventsViewHolder> {

        List<CareportalEvent> careportalEventList;

        RecyclerViewAdapter(List<CareportalEvent> careportalEventList) {
            this.careportalEventList = careportalEventList;
        }

        @NonNull
        @Override
        public CareportalEventsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_careportal_item, viewGroup, false);
            return new CareportalEventsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(CareportalEventsViewHolder holder, int position) {
            CareportalEvent careportalEvent = careportalEventList.get(position);
            holder.ns.setVisibility(NSUpload.isIdValid(careportalEvent._id) ? View.VISIBLE : View.GONE);
            holder.date.setText(DateUtil.dateAndTimeString(careportalEvent.date));
            holder.note.setText(careportalEvent.getNotes());
            holder.type.setText(Translator.translate(careportalEvent.eventType));
            holder.remove.setTag(careportalEvent);
        }

        @Override
        public int getItemCount() {
            return careportalEventList.size();
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        class CareportalEventsViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView date;
            TextView type;
            TextView note;
            TextView remove;
            TextView ns;

            CareportalEventsViewHolder(View itemView) {
                super(itemView);
                cv = itemView.findViewById(R.id.careportal_cardview);
                date = itemView.findViewById(R.id.careportal_date);
                type = itemView.findViewById(R.id.careportal_type);
                note = itemView.findViewById(R.id.careportal_note);
                ns = itemView.findViewById(R.id.ns_sign);
                remove = itemView.findViewById(R.id.careportal_remove);
                remove.setOnClickListener(v -> {
                    final CareportalEvent careportalEvent = (CareportalEvent) v.getTag();
                    OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.removerecord),
                            "\n" + MainApp.gs(R.string.careportal_newnstreatment_eventtype) + ": " + Translator.translate(careportalEvent.eventType) +
                                    "\n" + MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + careportalEvent.getNotes() +
                                    "\n" + MainApp.gs(R.string.date) + ": " + DateUtil.dateAndTimeString(careportalEvent.date),
                            (dialog, id) -> {
                                final String _id = careportalEvent._id;
                                if (NSUpload.isIdValid(_id)) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                } else {
                                    UploadQueue.removeID("dbAdd", _id);
                                }
                                MainApp.getDbHelper().delete(careportalEvent);
                            }, null);
                });
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_careportal_fragment, container, false);

        recyclerView = view.findViewById(R.id.careportal_recyclerview);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEvents(false));
        recyclerView.setAdapter(adapter);

        Button refreshFromNS = view.findViewById(R.id.careportal_refreshfromnightscout);
        refreshFromNS.setOnClickListener(v ->
                OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.careportal), MainApp.gs(R.string.refresheventsfromnightscout) + " ?", () -> {
                    MainApp.getDbHelper().resetCareportalEvents();
                    RxBus.INSTANCE.send(new EventNSClientRestart());
                }));

        view.findViewById(R.id.careportal_removeandroidapsstartedevents).setOnClickListener(v ->
                OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.careportal), MainApp.gs(R.string.careportal_removestartedevents), this::removeAndroidAPSStatedEvents));

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, true);
        if (nsUploadOnly)
            refreshFromNS.setVisibility(View.GONE);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventCareportalEventChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    private void updateGui() {
        recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEvents(false)), false);
    }

    private void removeAndroidAPSStatedEvents() {
        List<CareportalEvent> events = MainApp.getDbHelper().getCareportalEvents(false);
        for (int i = 0; i < events.size(); i++) {
            CareportalEvent careportalEvent = events.get(i);
            if (careportalEvent.json.contains(MainApp.gs(R.string.androidaps_start))) {
                final String _id = careportalEvent._id;
                if (NSUpload.isIdValid(_id)) {
                    NSUpload.removeCareportalEntryFromNS(_id);
                } else {
                    UploadQueue.removeID("dbAdd", _id);
                }
                MainApp.getDbHelper().delete(careportalEvent);
            }
        }
    }
}
