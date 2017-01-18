package info.nightscout.androidaps.plugins.TempTargetRange;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.j256.ormlite.dao.Dao;
import com.squareup.otto.Subscribe;

import java.sql.SQLException;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventTempTargetRangeChange;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 13/01/17.
 */

public class TempTargetRangeFragment extends Fragment implements View.OnClickListener, FragmentBase {

    private static TempTargetRangePlugin tempTargetRangePlugin = new TempTargetRangePlugin();

    public static TempTargetRangePlugin getPlugin() {
        return tempTargetRangePlugin;
    }

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    Button refreshFromNS;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempTargetsViewHolder> {

        List<TempTarget> tempTargetList;

        RecyclerViewAdapter(List<TempTarget> TempTargetList) {
            this.tempTargetList = TempTargetList;
        }

        @Override
        public TempTargetsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.temptargetrange_item, viewGroup, false);
            TempTargetsViewHolder TempTargetsViewHolder = new TempTargetsViewHolder(v);
            return TempTargetsViewHolder;
        }

        @Override
        public void onBindViewHolder(TempTargetsViewHolder holder, int position) {
            NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
            if (profile == null) return;
            TempTarget tempTarget = tempTargetList.get(position);
            holder.date.setText(DateUtil.dateAndTimeString(tempTarget.timeStart) + " - " + DateUtil.timeString(tempTargetList.get(position).getPlannedTimeEnd()));
            holder.duration.setText(DecimalFormatter.to0Decimal(tempTarget.duration) + " min");
            holder.low.setText(tempTarget.lowValueToUnitsToString(profile.getUnits()));
            holder.high.setText(tempTarget.highValueToUnitsToString(profile.getUnits()));
            holder.reason.setText(tempTarget.reason);
            if (tempTarget.isInProgress())
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.colorInProgress));
            else
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.cardColorBackground));
            holder.remove.setTag(tempTarget);
        }

        @Override
        public int getItemCount() {
            return tempTargetList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class TempTargetsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView duration;
            TextView low;
            TextView high;
            TextView reason;
            TextView remove;
            LinearLayout dateLinearLayout;

            TempTargetsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.temptargetrange_cardview);
                date = (TextView) itemView.findViewById(R.id.temptargetrange_date);
                duration = (TextView) itemView.findViewById(R.id.temptargetrange_duration);
                low = (TextView) itemView.findViewById(R.id.temptargetrange_low);
                high = (TextView) itemView.findViewById(R.id.temptargetrange_high);
                reason = (TextView) itemView.findViewById(R.id.temptargetrange_reason);
                remove = (TextView) itemView.findViewById(R.id.temptargetrange_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                dateLinearLayout = (LinearLayout) itemView.findViewById(R.id.temptargetrange_datelinearlayout);
            }

            @Override
            public void onClick(View v) {
                final TempTarget tempTarget = (TempTarget) v.getTag();
                final Context finalContext = context;
                switch (v.getId()) {
                    case R.id.temptargetrange_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                        builder.setMessage(MainApp.sResources.getString(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(tempTarget.timeStart));
                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = tempTarget._id;
                                if (_id != null && !_id.equals("")) {
                                    MainApp.getConfigBuilder().removeCareportalEntryFromNS(_id);
                                }
                                try {
                                    Dao<TempTarget, Long> daoTempTargets = MainApp.getDbHelper().getDaoTempTargets();
                                    daoTempTargets.delete(tempTarget);
                                    MainApp.bus().post(new EventTempTargetRangeChange());
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        builder.setNegativeButton(MainApp.sResources.getString(R.string.cancel), null);
                        builder.show();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.temptargetrange_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.temptargetrange_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(tempTargetRangePlugin.getList());
        recyclerView.setAdapter(adapter);

        refreshFromNS = (Button) view.findViewById(R.id.temptargetrange_refreshfromnightscout);
        refreshFromNS.setOnClickListener(this);

        context = getContext();

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.temptargetrange_refreshfromnightscout:
                SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getContext());
                boolean nsUploadOnly = SP.getBoolean("ns_upload_only", false);
                if (nsUploadOnly) {
                    ToastUtils.showToastInUiThread(getContext(), this.getContext().getString(R.string.ns_upload_only_enabled));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(this.getContext().getString(R.string.refreshtemptargetsfromnightscout));
                    builder.setPositiveButton(this.getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainApp.getDbHelper().resetTempTargets();
                            tempTargetRangePlugin.initializeData();
                            updateGUI();
                            Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                            MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                        }
                    });
                    builder.setNegativeButton(this.getContext().getString(R.string.cancel), null);
                    builder.show();
                }
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventTempTargetRangeChange ev) {
        updateGUI();
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(tempTargetRangePlugin.getList()), false);
                }
            });
    }
}
