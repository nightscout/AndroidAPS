package info.nightscout.androidaps.plugins.treatments.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 13/01/17.
 */

public class TreatmentsProfileSwitchFragment extends SubscriberFragment implements View.OnClickListener {
    private Logger log = LoggerFactory.getLogger(L.UI);

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    Button refreshFromNS;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ProfileSwitchViewHolder> {

        List<ProfileSwitch> profileSwitchList;

        RecyclerViewAdapter(List<ProfileSwitch> profileSwitchList) {
            this.profileSwitchList = profileSwitchList;
        }

        @Override
        public ProfileSwitchViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_profileswitch_item, viewGroup, false);
            return new ProfileSwitchViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ProfileSwitchViewHolder holder, int position) {
            Profile profile = ProfileFunctions.getInstance().getProfile();
            if (profile == null) return;
            ProfileSwitch profileSwitch = profileSwitchList.get(position);
            holder.ph.setVisibility(profileSwitch.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(NSUpload.isIdValid(profileSwitch._id) ? View.VISIBLE : View.GONE);

            holder.date.setText(DateUtil.dateAndTimeString(profileSwitch.date));
            if (!profileSwitch.isEndingEvent()) {
                holder.duration.setText(DecimalFormatter.to0Decimal(profileSwitch.durationInMinutes) + " min");
            } else {
                holder.duration.setText("");
            }
            holder.name.setText(profileSwitch.getCustomizedName());
            if (profileSwitch.isInProgress())
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.date.setTextColor(holder.duration.getCurrentTextColor());
            holder.remove.setTag(profileSwitch);
            holder.name.setTag(profileSwitch);
            holder.date.setTag(profileSwitch);
            holder.invalid.setVisibility(profileSwitch.isValid() ? View.GONE : View.VISIBLE);

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
            TextView invalid;

            ProfileSwitchViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.profileswitch_cardview);
                date = (TextView) itemView.findViewById(R.id.profileswitch_date);
                duration = (TextView) itemView.findViewById(R.id.profileswitch_duration);
                name = (TextView) itemView.findViewById(R.id.profileswitch_name);
                ph = (TextView) itemView.findViewById(R.id.pump_sign);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                invalid = (TextView) itemView.findViewById(R.id.invalid_sign);
                remove = (TextView) itemView.findViewById(R.id.profileswitch_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                name.setOnClickListener(this);
                date.setOnClickListener(this);

            }

            @Override
            public void onClick(View v) {
                final ProfileSwitch profileSwitch = (ProfileSwitch) v.getTag();
                if (profileSwitch == null) {
                    log.error("profileSwitch == null");
                    return;
                }
                switch (v.getId()) {
                    case R.id.profileswitch_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(profileSwitch.date));
                        builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = profileSwitch._id;
                                if (NSUpload.isIdValid(_id)) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                } else {
                                    UploadQueue.removeID("dbAdd", _id);
                                }
                                MainApp.getDbHelper().delete(profileSwitch);
                            }
                        });
                        builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                        builder.show();
                        break;
                    case R.id.profileswitch_date:
                    case R.id.profileswitch_name:
                        long time = ((ProfileSwitch) v.getTag()).date;
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

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getDbHelper().getProfileSwitchData(false));
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
                AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                builder.setTitle(MainApp.gs(R.string.confirmation));
                builder.setMessage(MainApp.gs(R.string.refresheventsfromnightscout) + "?");
                builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainApp.getDbHelper().resetProfileSwitch();
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
    public void onStatusEvent(final EventProfileNeedsUpdate ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getDbHelper().getProfileSwitchData(false)), false);
                }
            });
    }
}
