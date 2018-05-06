package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.Translator;

/**
 * Created by mike on 13/01/17.
 */

public class TreatmentsCareportalFragment extends SubscriberFragment implements View.OnClickListener {

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    Button refreshFromNS;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.CareportalEventsViewHolder> {

        List<CareportalEvent> careportalEventList;

        RecyclerViewAdapter(List<CareportalEvent> careportalEventList) {
            this.careportalEventList = careportalEventList;
        }

        @Override
        public CareportalEventsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_careportal_item, viewGroup, false);
            CareportalEventsViewHolder CareportalEventsViewHolder = new CareportalEventsViewHolder(v);
            return CareportalEventsViewHolder;
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
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class CareportalEventsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView type;
            TextView note;
            TextView remove;
            TextView ns;

            CareportalEventsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.careportal_cardview);
                date = (TextView) itemView.findViewById(R.id.careportal_date);
                type = (TextView) itemView.findViewById(R.id.careportal_type);
                note = (TextView) itemView.findViewById(R.id.careportal_note);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.careportal_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final CareportalEvent careportalEvent = (CareportalEvent) v.getTag();
                switch (v.getId()) {
                    case R.id.careportal_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(careportalEvent.date));
                        builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = careportalEvent._id;
                                if (NSUpload.isIdValid(_id)) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                } else {
                                    UploadQueue.removeID("dbAdd", _id);
                                }
                                MainApp.getDbHelper().delete(careportalEvent);
                            }
                        });
                        builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                        builder.show();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_careportal_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.careportal_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEventsFromTime(false));
        recyclerView.setAdapter(adapter);

        refreshFromNS = (Button) view.findViewById(R.id.careportal_refreshfromnightscout);
        refreshFromNS.setOnClickListener(this);

        context = getContext();

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if (nsUploadOnly)
            refreshFromNS.setVisibility(View.GONE);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.careportal_refreshfromnightscout:
                AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                builder.setTitle(MainApp.gs(R.string.confirmation));
                builder.setMessage(MainApp.gs(R.string.refresheventsfromnightscout) + " ?");
                builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainApp.getDbHelper().resetCareportalEvents();
                        Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                        MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    }
                });
                builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                builder.show();
                break;
        }

    }

    @Subscribe
    public void onStatusEvent(final EventCareportalEventChange ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEventsFromTime(false)), false);
                }
            });
    }
}
