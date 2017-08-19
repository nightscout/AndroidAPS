package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
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

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.SP;

/**
 * Created by mike on 13/01/17.
 */

public class TreatmentsProfileSwitchFragment extends SubscriberFragment implements View.OnClickListener {

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    Button refreshFromNS;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ProfileSwitchViewHolder> {

        ProfileIntervals<ProfileSwitch> profileSwitchList;

        RecyclerViewAdapter(ProfileIntervals<ProfileSwitch> profileSwitchList) {
            this.profileSwitchList = profileSwitchList;
        }

        @Override
        public ProfileSwitchViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_profileswitch_item, viewGroup, false);
            ProfileSwitchViewHolder ProfileSwitchViewHolder = new ProfileSwitchViewHolder(v);
            return ProfileSwitchViewHolder;
        }

        @Override
        public void onBindViewHolder(ProfileSwitchViewHolder holder, int position) {
            Profile profile = MainApp.getConfigBuilder().getProfile();
            if (profile == null) return;
            ProfileSwitch profileSwitch = profileSwitchList.getReversed(position);
            holder.ph.setVisibility(profileSwitch.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(profileSwitch._id != null ? View.VISIBLE : View.GONE);

            holder.date.setText(DateUtil.dateAndTimeString(profileSwitch.date));
            if (!profileSwitch.isEndingEvent()) {
                holder.duration.setText(DecimalFormatter.to0Decimal(profileSwitch.durationInMinutes) + " min");
            } else {
                holder.duration.setText("");
            }
            holder.name.setText(profileSwitch.profileName);
            if (profileSwitch.isInProgress())
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.date.setTextColor(holder.duration.getCurrentTextColor());
            holder.remove.setTag(profileSwitch);
            holder.name.setTag(profileSwitch);
            holder.date.setTag(profileSwitch);

        }

        @Override
        public int getItemCount() {
            return profileSwitchList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ProfileSwitchViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView duration;
            TextView name;
            TextView remove;
            TextView ph;
            TextView ns;

            ProfileSwitchViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.profileswitch_cardview);
                date = (TextView) itemView.findViewById(R.id.profileswitch_date);
                duration = (TextView) itemView.findViewById(R.id.profileswitch_duration);
                name = (TextView) itemView.findViewById(R.id.profileswitch_name);
                ph = (TextView) itemView.findViewById(R.id.pump_sign);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.profileswitch_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                name.setOnClickListener(this);
                date.setOnClickListener(this);

            }

            @Override
            public void onClick(View v) {
                final ProfileSwitch profileSwitch = (ProfileSwitch) v.getTag();
                switch (v.getId()) {
                    case R.id.profileswitch_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                        builder.setMessage(MainApp.sResources.getString(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(profileSwitch.date));
                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = profileSwitch._id;
                                if (_id != null && !_id.equals("")) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                }
                                MainApp.getDbHelper().delete(profileSwitch);
                            }
                        });
                        builder.setNegativeButton(MainApp.sResources.getString(R.string.cancel), null);
                        builder.show();
                        break;
                    case R.id.profileswitch_date:
                    case R.id.profileswitch_name:
                        long time = ((ProfileSwitch)v.getTag()).date;
                        ProfileViewerDialog pvd = ProfileViewerDialog.newInstance(time);
                        FragmentManager manager = getFragmentManager();
                        pvd.show(manager, "ProfileViewDialog");
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_profileswitch_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.profileswitch_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getConfigBuilder().getProfileSwitchesFromHistory());
        recyclerView.setAdapter(adapter);

        refreshFromNS = (Button) view.findViewById(R.id.profileswitch_refreshfromnightscout);
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
            case R.id.profileswitch_refreshfromnightscout:
                OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.confirmation), MainApp.sResources.getString(R.string.refresheventsfromnightscout) + "?", new Runnable() {
                    @Override
                    public void run() {
                        MainApp.getDbHelper().resetProfileSwitch();
                        Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                        MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    }
                });
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventProfileSwitchChange ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getConfigBuilder().getProfileSwitchesFromHistory()), false);
                }
            });
    }
}
