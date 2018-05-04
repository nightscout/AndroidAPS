package info.nightscout.androidaps.plugins.Source;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NSUpload;

/**
 * Created by mike on 16.10.2017.
 */

public class BGSourceFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(BGSourceFragment.class);

    RecyclerView recyclerView;

    Profile profile;

    final long MILLS_TO_THE_PAST = 12 * 60 * 60 * 1000L;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.bgsource_fragment, container, false);

            recyclerView = (RecyclerView) view.findViewById(R.id.bgsource_recyclerview);
            recyclerView.setHasFixedSize(true);
            LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
            recyclerView.setLayoutManager(llm);

            long now = System.currentTimeMillis();
            RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getDbHelper().getAllBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false));
            recyclerView.setAdapter(adapter);

            profile = MainApp.getConfigBuilder().getActiveProfileInterface().getProfile().getDefaultProfile();

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onStatusEvent(final EventNewBG ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    recyclerView.swapAdapter(new BGSourceFragment.RecyclerViewAdapter(MainApp.getDbHelper().getAllBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false)), true);
                }
            });
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.BgReadingsViewHolder> {

        List<BgReading> bgReadings;

        RecyclerViewAdapter(List<BgReading> bgReadings) {
            this.bgReadings = bgReadings;
        }

        @Override
        public BgReadingsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.bgsource_item, viewGroup, false);
            return new BgReadingsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(BgReadingsViewHolder holder, int position) {
            BgReading bgReading = bgReadings.get(position);
            holder.ns.setVisibility(NSUpload.isIdValid(bgReading._id) ? View.VISIBLE : View.GONE);
            holder.invalid.setVisibility(!bgReading.isValid ? View.VISIBLE : View.GONE);
            holder.date.setText(DateUtil.dateAndTimeString(bgReading.date));
            holder.value.setText(bgReading.valueToUnitsToString(profile.getUnits()));
            holder.direction.setText(bgReading.directionToSymbol());
            holder.remove.setTag(bgReading);
        }

        @Override
        public int getItemCount() {
            return bgReadings.size();
        }

        class BgReadingsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView date;
            TextView value;
            TextView direction;
            TextView invalid;
            TextView ns;
            TextView remove;

            BgReadingsViewHolder(View itemView) {
                super(itemView);
                date = (TextView) itemView.findViewById(R.id.bgsource_date);
                value = (TextView) itemView.findViewById(R.id.bgsource_value);
                direction = (TextView) itemView.findViewById(R.id.bgsource_direction);
                invalid = (TextView) itemView.findViewById(R.id.invalid_sign);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.bgsource_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final BgReading bgReading = (BgReading) v.getTag();
                switch (v.getId()) {

                    case R.id.bgsource_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(bgReading.date) + "\n" + bgReading.valueToUnitsToString(profile.getUnits()));
                        builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
/*                                final String _id = bgReading._id;
                                if (NSUpload.isIdValid(_id)) {
                                    NSUpload.removeFoodFromNS(_id);
                                } else {
                                    UploadQueue.removeID("dbAdd", _id);
                                }
*/
                                bgReading.isValid = false;
                                MainApp.getDbHelper().update(bgReading);
                                updateGUI();
                            }
                        });
                        builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                        builder.show();
                        break;

                }
            }
        }
    }

}
